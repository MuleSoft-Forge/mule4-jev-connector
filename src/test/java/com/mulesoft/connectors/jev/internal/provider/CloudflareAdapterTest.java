package com.mulesoft.connectors.jev.internal.provider;

import org.mule.runtime.http.api.HttpConstants;
import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.domain.DecisionResponse;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.http.HttpTransport;
import com.mulesoft.connectors.jev.internal.http.RawHttpResponse;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudflareAdapterTest {

  private static final String BASE = "https://api.cloudflare.com/client/v4/accounts/acct-1/ai/run";

  @Mock
  private HttpTransport transport;

  private DecisionRequest request() {
    ObjectNode questions = (ObjectNode) Json.read("{\"q\":{\"type\":\"noul\",\"instructions\":\"?\"}}");
    return new DecisionRequest(Json.read("{\"x\":1}"), null, questions, Map.of(), "qs", "v1");
  }

  private CloudflareAdapter adapter() {
    return new CloudflareAdapter("cloudflare", BASE, "typesafe/jev", Capabilities.full(false), "token", Map.of(),
        CostExtractor.NONE, RequestIdExtractor.NONE, transport);
  }

  @Test
  void nestsBodyUnderInputPostsToModelPathAndUnwrapsEnvelope() {
    String wrapped = "{\"result\":{\"model\":\"cf\",\"answers\":{\"q\":{\"type\":\"noul\",\"noul\":0.9}}},"
        + "\"success\":true,\"errors\":[]}";
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
    when(transport.send(any(HttpConstants.Method.class), url.capture(), anyMap(), body.capture()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, wrapped, Map.of())));

    DecisionResponse response = adapter().evaluate(request()).join();

    assertEquals("cf", response.model());
    assertEquals(0.9, response.answers().get("q").get("noul").asDouble(), 1e-9);
    assertEquals(BASE + "/typesafe/jev", url.getValue());

    JsonNode sent = Json.read(new String(body.getValue(), StandardCharsets.UTF_8));
    assertEquals("typesafe/jev", sent.get("model").asText());
    assertTrue(sent.get("input").has("state"));
    assertTrue(sent.get("input").has("questions"));
  }

  @Test
  void acceptsBareResponseWithoutEnvelope() {
    String bare = "{\"model\":\"cf\",\"answers\":{\"q\":{\"type\":\"noul\",\"noul\":0.1}}}";
    when(transport.send(any(), eq(BASE + "/typesafe/jev"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, bare, Map.of())));

    DecisionResponse response = adapter().evaluate(request()).join();

    assertEquals("cf", response.model());
  }

  @Test
  void treatsErrorEnvelopeAsProviderError() {
    String failed = "{\"result\":null,\"success\":false,\"errors\":[{\"code\":7003,\"message\":\"bad model\"}]}";
    when(transport.send(any(), any(), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, failed, Map.of())));

    CompletionException thrown = assertThrows(CompletionException.class, () -> adapter().evaluate(request()).join());
    ModuleException cause = assertInstanceOf(ModuleException.class, thrown.getCause());
    assertEquals(JevErrorType.PROVIDER_ERROR, cause.getType());
  }
}
