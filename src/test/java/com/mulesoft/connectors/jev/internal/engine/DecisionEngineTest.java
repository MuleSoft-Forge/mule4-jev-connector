package com.mulesoft.connectors.jev.internal.engine;

import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.jev.api.attributes.DecisionAttributes;
import com.mulesoft.connectors.jev.internal.connection.JevConnection;
import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.domain.DecisionResponse;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.http.ProviderHttpException;
import com.mulesoft.connectors.jev.internal.provider.ProviderAdapter;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecisionEngineTest {

  private final DecisionEngine engine = new DecisionEngine(new RetryPolicy(2, 500L, 5000L, 0.25, () -> 0.0),
      delayMs -> CompletableFuture.completedFuture(null));

  private DecisionRequest request() {
    ObjectNode questions = (ObjectNode) Json.read("{\"q\":{\"type\":\"choice\"}}");
    return new DecisionRequest(Json.read("{\"amount\":10}"), "gpt", questions, Map.of("q", "none"), "qs", "v2");
  }

  private DecisionResponse choiceResponse(BigDecimal reportedCost, Integer inputTokens) {
    ObjectNode answers = (ObjectNode) Json
        .read("{\"q\":{\"type\":\"choice\",\"choice\":\"a\",\"probabilities\":{\"a\":0.8,\"b\":0.2}}}");
    return DecisionResponse.builder().model("m").requestedModel("gpt").answers(answers).inputTokens(inputTokens)
        .outputTokens(2).providerReportedCost(reportedCost).providerRequestId("req-1").rawBody("{\"raw\":true}")
        .build();
  }

  private JevConnection connection(ProviderAdapter adapter) {
    when(adapter.routeName()).thenReturn("typesafe");
    return new JevConnection(adapter, List.of());
  }

  @Test
  void enrichesAnswersAndEstimatesCostFromTokens() {
    ProviderAdapter adapter = mock(ProviderAdapter.class);
    when(adapter.evaluate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(CompletableFuture.completedFuture(choiceResponse(null, 1_000_000)));

    DecisionOutcome outcome = engine
        .evaluate(connection(adapter), request(), new DecisionContext(new BigDecimal("0.042"), true)).join();

    // derived block computed
    assertEquals(0.6, outcome.payload().get("q").get("derived").get("margin").asDouble(), 1e-9);
    assertEquals("b", outcome.payload().get("q").get("derived").get("runnerUp").asText());
    assertFalse(outcome.payload().get("q").get("derived").get("isNoMatch").asBoolean());

    DecisionAttributes attributes = outcome.attributes();
    assertEquals("typesafe", attributes.getProvider());
    assertEquals("gpt", attributes.getRequestedModel());
    assertEquals("ESTIMATE", attributes.getCostSource());
    assertEquals(0, new BigDecimal("0.042").compareTo(attributes.getEstimatedCostUsd()));
    assertEquals(1, attributes.getAttempts());
    assertEquals("{\"raw\":true}", attributes.getRawResponse());
    assertEquals("qs", attributes.getQuestionSetId());
  }

  @Test
  void prefersProviderReportedCost() {
    ProviderAdapter adapter = mock(ProviderAdapter.class);
    when(adapter.evaluate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(CompletableFuture.completedFuture(choiceResponse(new BigDecimal("0.005"), 100)));

    DecisionOutcome outcome = engine
        .evaluate(connection(adapter), request(), new DecisionContext(new BigDecimal("0.042"), false)).join();

    assertEquals("PROVIDER", outcome.attributes().getCostSource());
    assertEquals(new BigDecimal("0.005"), outcome.attributes().getEstimatedCostUsd());
    assertNull(outcome.attributes().getRawResponse());
  }

  @Test
  void retriesTransientFailureThenSucceeds() {
    ProviderAdapter adapter = mock(ProviderAdapter.class);
    when(adapter.evaluate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(CompletableFuture.failedFuture(new ProviderHttpException(500, "boom", OptionalLong.empty())))
        .thenReturn(CompletableFuture.completedFuture(choiceResponse(null, 10)));

    DecisionOutcome outcome = engine.evaluate(connection(adapter), request(), new DecisionContext(null, false)).join();

    assertEquals(2, outcome.attributes().getAttempts());
    assertEquals("NONE", outcome.attributes().getCostSource());
  }

  @Test
  void mapsExhaustedRetriesToProviderError() {
    ProviderAdapter adapter = mock(ProviderAdapter.class);
    when(adapter.evaluate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(CompletableFuture.failedFuture(new ProviderHttpException(500, "boom", OptionalLong.empty())));

    CompletionException thrown = assertThrows(CompletionException.class,
        () -> engine.evaluate(connection(adapter), request(), new DecisionContext(null, false)).join());
    ModuleException cause = assertInstanceOf(ModuleException.class, thrown.getCause());
    assertEquals(JevErrorType.PROVIDER_ERROR, cause.getType());
  }

  @Test
  void doesNotRetryNonTransientStatus() {
    ProviderAdapter adapter = mock(ProviderAdapter.class);
    when(adapter.evaluate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(CompletableFuture.failedFuture(new ProviderHttpException(401, "no", OptionalLong.empty())));

    CompletionException thrown = assertThrows(CompletionException.class,
        () -> engine.evaluate(connection(adapter), request(), new DecisionContext(null, false)).join());
    ModuleException cause = assertInstanceOf(ModuleException.class, thrown.getCause());
    assertEquals(JevErrorType.UNAUTHORIZED, cause.getType());
  }

  @Test
  void mapsTransportFailureToConnectivity() {
    ProviderAdapter adapter = mock(ProviderAdapter.class);
    when(adapter.evaluate(org.mockito.ArgumentMatchers.any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("dns")));

    CompletionException thrown = assertThrows(CompletionException.class,
        () -> engine.evaluate(connection(adapter), request(), new DecisionContext(null, false)).join());
    ModuleException cause = assertInstanceOf(ModuleException.class, thrown.getCause());
    assertEquals(JevErrorType.CONNECTIVITY, cause.getType());
  }
}
