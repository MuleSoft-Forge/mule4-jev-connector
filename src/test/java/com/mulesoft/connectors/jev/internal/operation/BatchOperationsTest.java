package com.mulesoft.connectors.jev.internal.operation;

import org.mule.sdk.api.exception.ModuleException;
import org.mule.sdk.api.runtime.operation.Result;
import org.mule.sdk.api.runtime.process.CompletionCallback;

import com.mulesoft.connectors.jev.api.attributes.BatchAttributes;
import com.mulesoft.connectors.jev.internal.config.JevConfiguration;
import com.mulesoft.connectors.jev.internal.connection.JevConnection;
import com.mulesoft.connectors.jev.internal.engine.BudgetGuard;
import com.mulesoft.connectors.jev.internal.engine.DecisionEngine;
import com.mulesoft.connectors.jev.internal.engine.RetryPolicy;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.provider.MockAdapter;
import com.mulesoft.connectors.jev.internal.provider.ProviderAdapter;
import com.mulesoft.connectors.jev.internal.support.InMemoryObjectStore;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchOperationsTest {

  private static final long HOUR = 3_600_000L;
  private static final String NOUL_QUESTION = "{\"decision\":{\"type\":\"noul\",\"instructions\":\"ok?\"}}";

  private final BatchOperations operations = new BatchOperations();
  private final DecisionEngine engine = new DecisionEngine(new RetryPolicy(2, 500L, 5000L, 0.25, () -> 0.0),
      delayMs -> CompletableFuture.completedFuture(null));

  private JevConfiguration config() {
    return mock(JevConfiguration.class);
  }

  private JevConnection connection(ProviderAdapter adapter) {
    return new JevConnection(adapter, List.of(), engine);
  }

  private static InputStream json(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void evaluatesEveryItemAndReportsTotals() {
    Capture<BatchAttributes> capture = new Capture<>();
    operations.evaluateBatch(config(), connection(new MockAdapter(0.9, 0)),
        json("[{\"id\":\"a\"},{\"id\":\"b\"},{\"id\":\"c\"}]"), json(NOUL_QUESTION), null, null, null, "id", 4, true,
        1000, false, new RequestOptions(), capture);

    JsonNode payload = capture.payload();
    assertEquals(3, payload.size());
    assertEquals("a", payload.get(0).get("key").asText());
    assertEquals("OK", payload.get(0).get("status").asText());
    assertNotNull(payload.get(0).get("answers").get("decision"));

    BatchAttributes attributes = capture.attributes;
    assertEquals(3, attributes.getTotal());
    assertEquals(3, attributes.getSucceeded());
    assertEquals(0, attributes.getFailed());
    assertEquals(0, attributes.getSkippedBudget());
  }

  @Test
  void deduplicatesIdenticalStatesIntoOneCall() {
    ProviderAdapter adapter = spy(new MockAdapter(0.9, 0));
    Capture<BatchAttributes> capture = new Capture<>();
    operations.evaluateBatch(config(), connection(adapter), json("[{\"id\":\"a\"},{\"id\":\"a\"},{\"id\":\"b\"}]"),
        json(NOUL_QUESTION), null, null, null, "id", 4, true, 1000, false, new RequestOptions(), capture);

    // Two identical states collapse to one billed call; the third distinct state is the second.
    verify(adapter, times(2)).evaluate(any());
    assertEquals(3, capture.payload().size());
    assertEquals(3, capture.attributes.getSucceeded());
  }

  @Test
  void doesNotDeduplicateWhenTurnedOff() {
    ProviderAdapter adapter = spy(new MockAdapter(0.9, 0));
    Capture<BatchAttributes> capture = new Capture<>();
    operations.evaluateBatch(config(), connection(adapter), json("[{\"id\":\"a\"},{\"id\":\"a\"}]"),
        json(NOUL_QUESTION), null, null, null, "id", 4, false, 1000, false, new RequestOptions(), capture);

    verify(adapter, times(2)).evaluate(any());
    assertEquals(2, capture.payload().size());
  }

  @Test
  void budgetLimitTurnsLaterItemsIntoSkipped() {
    JevConfiguration config = config();
    when(config.isBudgetEnabled()).thenReturn(true);
    when(config.getBudgetMaxCallsPerWindow()).thenReturn(2L);
    when(config.getBudgetMaxInputTokensPerWindow()).thenReturn(null);
    when(config.budgetWindowMillis()).thenReturn(HOUR);
    JevConnection connection = new JevConnection(new MockAdapter(0.9, 0), List.of(), engine, null,
        new BudgetGuard(new InMemoryObjectStore()), null);

    Capture<BatchAttributes> capture = new Capture<>();
    operations.evaluateBatch(config, connection, json("[{\"id\":\"a\"},{\"id\":\"b\"},{\"id\":\"c\"},{\"id\":\"d\"}]"),
        json(NOUL_QUESTION), null, null, null, "id", 1, true, 1000, false, new RequestOptions(), capture);

    BatchAttributes attributes = capture.attributes;
    assertEquals(4, attributes.getTotal());
    assertEquals(2, attributes.getSucceeded());
    assertEquals(2, attributes.getSkippedBudget());

    long skipped = countStatus(capture.payload(), "SKIPPED_BUDGET");
    assertEquals(2, skipped);
  }

  @Test
  void rejectsABatchOverTheItemLimit() {
    Capture<BatchAttributes> capture = new Capture<>();
    operations.evaluateBatch(config(), connection(new MockAdapter(0.9, 0)), json("[{\"id\":\"a\"},{\"id\":\"b\"}]"),
        json(NOUL_QUESTION), null, null, null, "id", 4, true, 1, false, new RequestOptions(), capture);

    assertNull(capture.result);
    assertNotNull(capture.error);
    assertEquals(JevErrorType.BATCH_TOO_LARGE, capture.error.getType());
  }

  @Test
  void filterKeepsItemsClearingTheThreshold() {
    Capture<BatchAttributes> capture = new Capture<>();
    operations.filter(config(), connection(new MockAdapter(0.8, 0)), json("[{\"t\":\"x\"},{\"t\":\"y\"}]"), "relevant?",
        0.5, 20, "t", 4, new RequestOptions(), capture);

    JsonNode payload = capture.payload();
    assertEquals(2, payload.get("kept").size());
    assertEquals(0, payload.get("dropped").size());
    assertEquals(2, payload.get("scores").size());
    assertEquals(0, payload.get("scores").get(0).get("index").asInt());
    assertTrue(payload.get("scores").get(0).get("kept").asBoolean());
    assertEquals(2, capture.attributes.getTotal());
  }

  @Test
  void filterDropsItemsBelowTheThreshold() {
    Capture<BatchAttributes> capture = new Capture<>();
    operations.filter(config(), connection(new MockAdapter(0.8, 0)), json("[{\"t\":\"x\"},{\"t\":\"y\"}]"), "relevant?",
        0.95, 20, null, 4, new RequestOptions(), capture);

    JsonNode payload = capture.payload();
    assertEquals(0, payload.get("kept").size());
    assertEquals(2, payload.get("dropped").size());
  }

  @Test
  void filterChunksAcrossMultipleCalls() {
    ProviderAdapter adapter = spy(new MockAdapter(0.8, 0));
    Capture<BatchAttributes> capture = new Capture<>();
    operations.filter(config(), connection(adapter), json("[{\"t\":\"a\"},{\"t\":\"b\"},{\"t\":\"c\"}]"), "relevant?",
        0.5, 2, "t", 4, new RequestOptions(), capture);

    // Three items at chunkSize 2 is two chunks, so two billed calls.
    verify(adapter, times(2)).evaluate(any());
    assertEquals(3, capture.payload().get("scores").size());
  }

  private static long countStatus(JsonNode payload, String status) {
    long count = 0;
    for (JsonNode item : payload) {
      if (status.equals(item.path("status").asText())) {
        count++;
      }
    }
    return count;
  }

  /** Captures whichever terminal the operation invokes, so a synchronous mock run can be asserted inline. */
  private static final class Capture<A> implements CompletionCallback<InputStream, A> {

    private Result<InputStream, A> result;
    private ModuleException error;
    private A attributes;
    private final AtomicReference<JsonNode> parsed = new AtomicReference<>();

    @Override
    public void success(Result<InputStream, A> value) {
      this.result = value;
      this.attributes = value.getAttributes().orElse(null);
      this.parsed.set(Json.read(value.getOutput()));
    }

    @Override
    public void error(Throwable throwable) {
      this.error = (ModuleException) throwable;
    }

    JsonNode payload() {
      return parsed.get();
    }
  }
}
