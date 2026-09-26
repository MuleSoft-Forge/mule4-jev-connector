package com.mulesoft.connectors.jev.internal.provider;

import org.mule.runtime.http.api.HttpConstants;
import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.domain.DecisionResponse;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.http.HttpTransport;
import com.mulesoft.connectors.jev.internal.http.ProviderHttpException;
import com.mulesoft.connectors.jev.internal.http.RawHttpResponse;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemOneAdapterTest {

  @Mock
  private HttpTransport transport;

  private DecisionRequest request() {
    ObjectNode questions = (ObjectNode) Json.read("{\"q\":{\"type\":\"noul\",\"instructions\":\"?\"}}");
    return new DecisionRequest(Json.read("{\"x\":1}"), null, questions, Map.of(), "qs", "v1");
  }

  private SystemOneAdapter adapter(CostExtractor cost) {
    return new SystemOneAdapter("typesafe", "https://api.typesafe.ai/", "default-model", Capabilities.full(true), "key",
        Map.of(), cost, RequestIdExtractor.header("x-request-id"), transport);
  }

  @Test
  void parsesSuccessfulResponse() {
    String body = "{\"model\":\"m-1\",\"answers\":{\"q\":{\"type\":\"noul\",\"noul\":0.9}},"
        + "\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}";
    when(transport.send(any(HttpConstants.Method.class), eq("https://api.typesafe.ai/v1/systemone"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, body, Map.of("x-request-id", "req-9"))));

    DecisionResponse response = adapter(CostExtractor.NONE).evaluate(request()).join();

    assertEquals("m-1", response.model());
    assertEquals("default-model", response.requestedModel());
    assertEquals(12, response.inputTokens());
    assertEquals(3, response.outputTokens());
    assertEquals("req-9", response.providerRequestId());
    assertEquals(0.9, response.answers().get("q").get("noul").asDouble(), 1e-9);
    assertNull(response.providerReportedCost());
  }

  @Test
  void extractsProviderReportedCost() {
    String body = "{\"answers\":{\"q\":{\"type\":\"noul\"}},\"usage\":{\"input_tokens\":5,\"cost\":0.0021}}";
    when(transport.send(any(), any(), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, body, Map.of())));

    DecisionResponse response = adapter(CostExtractor.OPENROUTER).evaluate(request()).join();

    assertEquals(new BigDecimal("0.0021"), response.providerReportedCost());
  }

  @Test
  void throwsProviderHttpExceptionWithRetryAfterOnNon2xx() {
    when(transport.send(any(), any(), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(
            new RawHttpResponse(429, "{\"error\":\"slow\"}", Map.of("retry-after-ms", "1500"))));

    CompletionException thrown = assertThrows(CompletionException.class,
        () -> adapter(CostExtractor.NONE).evaluate(request()).join());
    ProviderHttpException cause = assertInstanceOf(ProviderHttpException.class, thrown.getCause());
    assertEquals(429, cause.status());
    assertEquals(1500L, cause.retryAfterMs().getAsLong());
  }

  @Test
  void listsModelsFromModelsEndpoint() {
    String body = "{\"models\":[{\"name\":\"jev-latest\"},{\"name\":\"jev-1.13.0\"}]}";
    when(transport.send(any(HttpConstants.Method.class), eq("https://api.typesafe.ai/v1/models"), anyMap(), any()))
        .thenReturn(CompletableFuture.completedFuture(new RawHttpResponse(200, body, Map.of())));

    List<String> models = adapter(CostExtractor.NONE).listModels().join();

    assertEquals(List.of("jev-latest", "jev-1.13.0"), models);
  }

  @Test
  void listModelsIsUnsupportedWhenCapabilityAbsent() {
    SystemOneAdapter adapter = new SystemOneAdapter("compatible", "https://gw", "m", Capabilities.full(false), null,
        Map.of(), CostExtractor.NONE, RequestIdExtractor.NONE, transport);

    CompletionException thrown = assertThrows(CompletionException.class, () -> adapter.listModels().join());
    ModuleException cause = assertInstanceOf(ModuleException.class, thrown.getCause());
    assertEquals(JevErrorType.UNSUPPORTED_BY_PROVIDER, cause.getType());
  }
}
