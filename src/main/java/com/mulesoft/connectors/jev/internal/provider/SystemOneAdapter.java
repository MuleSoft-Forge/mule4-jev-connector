package com.mulesoft.connectors.jev.internal.provider;

import org.mule.runtime.http.api.HttpConstants;
import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.domain.DecisionResponse;
import com.mulesoft.connectors.jev.internal.engine.RetryPolicy;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.http.HttpTransport;
import com.mulesoft.connectors.jev.internal.http.ProviderHttpException;
import com.mulesoft.connectors.jev.internal.http.RawHttpResponse;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Adapter for every route that speaks the canonical TypeSafe {@code systemOne} contract: TypeSafe direct, OpenRouter,
 * Vercel AI Gateway and any compatible gateway. It is parameterised by base URL, model, cost extractor and request-id
 * strategy, so one class serves four routes. The Cloudflare route subclasses it to reshape the request and unwrap the
 * v4 envelope.
 */
public class SystemOneAdapter implements ProviderAdapter {

  private final String routeName;
  private final String baseUrl;
  private final String defaultModel;
  private final Capabilities capabilities;
  private final String apiKey;
  private final Map<String, String> extraHeaders;
  private final CostExtractor costExtractor;
  private final RequestIdExtractor requestIdExtractor;
  private final HttpTransport transport;

  public SystemOneAdapter(String routeName, String baseUrl, String defaultModel, Capabilities capabilities,
      String apiKey, Map<String, String> extraHeaders, CostExtractor costExtractor,
      RequestIdExtractor requestIdExtractor, HttpTransport transport) {
    this.routeName = routeName;
    this.baseUrl = trimTrailingSlash(baseUrl);
    this.defaultModel = defaultModel;
    this.capabilities = capabilities;
    this.apiKey = apiKey;
    this.extraHeaders = extraHeaders == null ? Map.of() : Map.copyOf(extraHeaders);
    this.costExtractor = costExtractor == null ? CostExtractor.NONE : costExtractor;
    this.requestIdExtractor = requestIdExtractor == null ? RequestIdExtractor.NONE : requestIdExtractor;
    this.transport = transport;
  }

  /** The base URL, trailing slash stripped, for subclasses that build their own endpoints. */
  protected final String baseUrl() {
    return baseUrl;
  }

  @Override
  public String routeName() {
    return routeName;
  }

  @Override
  public Capabilities capabilities() {
    return capabilities;
  }

  @Override
  public CompletableFuture<DecisionResponse> evaluate(DecisionRequest request) {
    String model = request.requestedModel() != null ? request.requestedModel() : defaultModel;
    byte[] body = buildBody(request, model).getBytes(StandardCharsets.UTF_8);

    return transport.send(HttpConstants.Method.POST, endpoint(model), jsonHeaders(), body)
        .thenApply(response -> parse(response, model));
  }

  @Override
  public CompletableFuture<List<String>> listModels() {
    if (!capabilities.isSupportsModelList()) {
      return CompletableFuture.failedFuture(new ModuleException(
          "The " + routeName + " route does not support model listing", JevErrorType.UNSUPPORTED_BY_PROVIDER));
    }
    return transport.send(HttpConstants.Method.GET, baseUrl + "/v1/models", jsonHeaders(), null)
        .thenApply(this::parseModels);
  }

  /** Headers shared by every request: auth (when keyed), plus JSON content negotiation and the route's extras. */
  private Map<String, String> jsonHeaders() {
    Map<String, String> headers = new HashMap<>(extraHeaders);
    if (apiKey != null && !apiKey.isBlank()) {
      headers.put("Authorization", "Bearer " + apiKey);
    }
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");
    return headers;
  }

  /** The decision endpoint. Overridden by the Cloudflare adapter, which posts to {@code {baseUrl}/{model}}. */
  protected String endpoint(String model) {
    return baseUrl + "/v1/systemone";
  }

  /** Builds the request body. Overridden by the Cloudflare adapter, which nests under {@code input}. */
  protected String buildBody(DecisionRequest request, String model) {
    ObjectNode node = Json.object();
    node.set("state", request.state());
    node.put("model", model);
    node.set("questions", request.questions());
    return Json.write(node);
  }

  private List<String> parseModels(RawHttpResponse response) {
    if (!response.isSuccess()) {
      OptionalLong retryAfter = RetryPolicy.parseRetryAfter(response.header("retry-after-ms"),
          response.header("retry-after"), Instant.now());
      throw new ProviderHttpException(response.status(), response.body(), retryAfter);
    }
    JsonNode root;
    try {
      root = Json.read(response.body());
    } catch (RuntimeException e) {
      throw new ModuleException("Model list body was not valid JSON", JevErrorType.INVALID_RESPONSE, e);
    }
    List<String> ids = new ArrayList<>();
    JsonNode models = unwrap(root).path("models");
    if (models.isArray()) {
      for (JsonNode model : models) {
        JsonNode name = model.isObject() ? model.get("name") : model;
        if (name != null && name.isTextual() && !name.asText().isBlank()) {
          ids.add(name.asText());
        }
      }
    }
    return ids;
  }

  private DecisionResponse parse(RawHttpResponse response, String sentModel) {
    if (!response.isSuccess()) {
      OptionalLong retryAfter = RetryPolicy.parseRetryAfter(response.header("retry-after-ms"),
          response.header("retry-after"), Instant.now());
      throw new ProviderHttpException(response.status(), response.body(), retryAfter);
    }

    JsonNode root;
    try {
      root = Json.read(response.body());
    } catch (RuntimeException e) {
      throw new ModuleException("Response body was not valid JSON", JevErrorType.INVALID_RESPONSE, e);
    }
    JsonNode body = unwrap(root);

    JsonNode answers = body.get("answers");
    if (answers == null || !answers.isObject()) {
      throw new ModuleException("Response has no 'answers' object", JevErrorType.INVALID_RESPONSE);
    }

    BigDecimal cost = costExtractor.extract(body);
    String requestId = requestIdExtractor.extract(response, body);

    return DecisionResponse.builder().model(body.path("model").asText(sentModel)).requestedModel(sentModel)
        .answers((ObjectNode) answers).inputTokens(intOrNull(body.path("usage").path("input_tokens")))
        .outputTokens(intOrNull(body.path("usage").path("output_tokens"))).providerReportedCost(cost)
        .providerRequestId(requestId).rawBody(response.body()).build();
  }

  /** SystemOne responses are unwrapped already. Cloudflare overrides to peel off {@code result}. */
  protected JsonNode unwrap(JsonNode root) {
    return root;
  }

  private static Integer intOrNull(JsonNode node) {
    return node != null && node.isNumber() ? node.asInt() : null;
  }

  private static String trimTrailingSlash(String url) {
    if (url == null) {
      return null;
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
