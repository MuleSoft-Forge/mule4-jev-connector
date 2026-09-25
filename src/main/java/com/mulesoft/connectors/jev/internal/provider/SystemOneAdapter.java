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
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Adapter for every route that speaks the canonical TypeSafe {@code systemOne} contract: TypeSafe direct, OpenRouter,
 * Vercel AI Gateway and any compatible gateway. It is parameterised by base URL, model, cost extractor and request-id
 * header, so one class serves four routes.
 */
public class SystemOneAdapter implements ProviderAdapter {

  private final String routeName;
  private final String baseUrl;
  private final String defaultModel;
  private final Capabilities capabilities;
  private final String apiKey;
  private final Map<String, String> extraHeaders;
  private final CostExtractor costExtractor;
  private final String requestIdHeader;
  private final HttpTransport transport;

  public SystemOneAdapter(String routeName, String baseUrl, String defaultModel, Capabilities capabilities,
      String apiKey, Map<String, String> extraHeaders, CostExtractor costExtractor, String requestIdHeader,
      HttpTransport transport) {
    this.routeName = routeName;
    this.baseUrl = trimTrailingSlash(baseUrl);
    this.defaultModel = defaultModel;
    this.capabilities = capabilities;
    this.apiKey = apiKey;
    this.extraHeaders = extraHeaders == null ? Map.of() : Map.copyOf(extraHeaders);
    this.costExtractor = costExtractor == null ? CostExtractor.NONE : costExtractor;
    this.requestIdHeader = requestIdHeader;
    this.transport = transport;
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

    Map<String, String> headers = new HashMap<>(extraHeaders);
    if (apiKey != null && !apiKey.isBlank()) {
      headers.put("Authorization", "Bearer " + apiKey);
    }
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");

    return transport.send(HttpConstants.Method.POST, baseUrl + "/v1/systemone", headers, body)
        .thenApply(response -> parse(response, model));
  }

  /** Builds the request body. Overridden by the Cloudflare adapter, which nests under {@code input}. */
  protected String buildBody(DecisionRequest request, String model) {
    ObjectNode node = Json.object();
    node.set("state", request.state());
    node.put("model", model);
    node.set("questions", request.questions());
    return Json.write(node);
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
    String requestId = requestIdHeader == null ? null : response.header(requestIdHeader);

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
