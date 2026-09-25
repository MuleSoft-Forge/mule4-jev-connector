package com.mulesoft.connectors.jev.internal.provider;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Pulls a route's self-reported cost (USD) out of a response body, or returns {@code null} when the route reports none
 * (in which case the engine estimates from tokens). Different gateways expose cost in different places, so each route
 * supplies its own extractor.
 */
@FunctionalInterface
public interface CostExtractor {

  /** No provider-reported cost; the engine estimates from tokens. */
  CostExtractor NONE = root -> null;

  /** OpenRouter reports {@code usage.cost} in USD. */
  CostExtractor OPENROUTER = root -> number(root.path("usage").path("cost"));

  /** Vercel reports {@code provider_metadata.gateway.cost} as a string in USD. */
  CostExtractor VERCEL = root -> number(root.path("provider_metadata").path("gateway").path("cost"));

  BigDecimal extract(JsonNode responseRoot);

  static BigDecimal number(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    try {
      if (node.isNumber()) {
        return node.decimalValue();
      }
      if (node.isTextual() && !node.asText().isBlank()) {
        return new BigDecimal(node.asText().trim());
      }
    } catch (NumberFormatException ignored) {
      return null;
    }
    return null;
  }
}
