package com.mulesoft.connectors.jev.internal.provider;

import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.http.HttpTransport;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Cloudflare Workers AI route. It runs the same TypeSafe decision model but through Cloudflare's {@code /ai/run}
 * surface, which differs in three ways: the model id is part of the path ({@code {baseUrl}/{model}}), the request nests
 * the decision under {@code input}, and the response may be wrapped in the Cloudflare v4 envelope
 * ({@code result}/{@code success}/{@code errors}). Cloudflare does not enumerate models, so
 * {@code capabilities.supportsModelList} is false.
 */
public class CloudflareAdapter extends SystemOneAdapter {

  public CloudflareAdapter(String routeName, String baseUrl, String defaultModel, Capabilities capabilities,
      String apiKey, Map<String, String> extraHeaders, CostExtractor costExtractor,
      RequestIdExtractor requestIdExtractor, HttpTransport transport) {
    super(routeName, baseUrl, defaultModel, capabilities, apiKey, extraHeaders, costExtractor, requestIdExtractor,
        transport);
  }

  @Override
  protected String endpoint(String model) {
    return baseUrl() + "/" + model;
  }

  @Override
  protected String buildBody(DecisionRequest request, String model) {
    ObjectNode node = Json.object();
    node.put("model", model);
    ObjectNode input = node.putObject("input");
    input.set("state", request.state());
    input.set("questions", request.questions());
    return Json.write(node);
  }

  /**
   * Accepts both the bare TypeSafe response and the Cloudflare v4 envelope. A {@code success:false} flag or a non-empty
   * {@code errors} array is treated as a provider error; otherwise the payload under {@code result} is returned (or the
   * root itself when the response is bare).
   */
  @Override
  protected JsonNode unwrap(JsonNode root) {
    boolean enveloped = root.has("result") || root.has("success") || root.has("errors");
    if (!enveloped) {
      return root;
    }
    JsonNode errors = root.path("errors");
    boolean failed = (root.has("success") && !root.path("success").asBoolean(true))
        || (errors.isArray() && !errors.isEmpty());
    if (failed) {
      String message = errors.isArray() && !errors.isEmpty() ? errors.toString() : "Cloudflare reported success:false";
      throw new ModuleException("Cloudflare returned an error envelope: " + message, JevErrorType.PROVIDER_ERROR);
    }
    JsonNode result = root.get("result");
    return result != null && !result.isNull() ? result : root;
  }
}
