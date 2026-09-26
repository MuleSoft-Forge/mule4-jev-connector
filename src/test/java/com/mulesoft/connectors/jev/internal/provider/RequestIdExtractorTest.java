package com.mulesoft.connectors.jev.internal.provider;

import com.mulesoft.connectors.jev.internal.http.RawHttpResponse;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestIdExtractorTest {

  @Test
  void openRouterPrefersGenerationIdHeader() {
    RawHttpResponse response = new RawHttpResponse(200, "{}",
        Map.of("x-generation-id", "gen-from-header", "x-request-id", "ignored"));
    JsonNode body = Json.read("{\"id\":\"gen-from-body\"}");

    assertEquals("gen-from-header", RequestIdExtractor.OPENROUTER.extract(response, body));
  }

  @Test
  void openRouterFallsBackToBodyIdWhenHeaderMissing() {
    RawHttpResponse response = new RawHttpResponse(200, "{}", Map.of());
    JsonNode body = Json.read("{\"id\":\"gen-from-body\"}");

    assertEquals("gen-from-body", RequestIdExtractor.OPENROUTER.extract(response, body));
  }

  @Test
  void openRouterReturnsNullWhenNeitherHeaderNorBodyIdPresent() {
    RawHttpResponse response = new RawHttpResponse(200, "{}", Map.of("x-request-id", "not-used"));
    JsonNode body = Json.read("{\"answers\":{}}");

    assertNull(RequestIdExtractor.OPENROUTER.extract(response, body));
  }

  @Test
  void typeSafeHeaderExtractorReadsTypesafeRequestId() {
    RawHttpResponse response = new RawHttpResponse(200, "{}", Map.of("x-typesafe-request-id", "req_abc"));

    assertEquals("req_abc", RequestIdExtractor.header("x-typesafe-request-id").extract(response, Json.object()));
  }
}
