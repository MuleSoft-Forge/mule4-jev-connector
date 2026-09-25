package com.mulesoft.connectors.jev.internal.provider;

import com.mulesoft.connectors.jev.internal.http.RawHttpResponse;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Pulls the provider's own request/trace id out of a response so it can be recorded in
 * {@code attributes.providerRequestId} for support correlation. Routes disagree on where it lives — a header on
 * TypeSafe and compatible gateways, a body field on Vercel — so each route supplies its own strategy.
 */
@FunctionalInterface
public interface RequestIdExtractor {

  /** No request id is exposed by this route. */
  RequestIdExtractor NONE = (response, body) -> null;

  /** Vercel exposes it as {@code provider_metadata.gateway.generationId} in the response body. */
  RequestIdExtractor VERCEL_GENERATION_ID = (response,
      body) -> textOrNull(body.path("provider_metadata").path("gateway").path("generationId"));

  /**
   * @param response
   *          the raw response (for header-based ids).
   * @param body
   *          the unwrapped response body (for field-based ids); never {@code null}.
   * @return the request id, or {@code null} when this route does not report one.
   */
  String extract(RawHttpResponse response, JsonNode body);

  /** A strategy that reads a single response header, case-insensitively. */
  static RequestIdExtractor header(String name) {
    return (response, body) -> response.header(name);
  }

  static String textOrNull(JsonNode node) {
    return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
  }
}
