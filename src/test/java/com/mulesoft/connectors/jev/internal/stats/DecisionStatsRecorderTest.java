package com.mulesoft.connectors.jev.internal.stats;

import com.mulesoft.connectors.jev.api.attributes.DecisionAttributes;
import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.engine.DecisionOutcome;
import com.mulesoft.connectors.jev.internal.support.InMemoryObjectStore;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionStatsRecorderTest {

  private final DecisionStatsRecorder recorder = new DecisionStatsRecorder(new InMemoryObjectStore());

  @Test
  void aggregatesDistributionNoMatchAndConfidenceInTheCurrentWindow() {
    recorder.record(req(), decision("billing", 0.9, false), 100);
    recorder.record(req(), decision("billing", 0.7, false), 100);
    recorder.record(req(), decision("technical", 0.5, true), 100);

    DecisionStatsRecorder.SetStats stats = recorder.windowsFor("triage");
    DecisionStatsRecorder.Window current = stats.current();
    assertEquals(3, current.count());
    assertEquals(2L, current.distribution().get("route").get("billing"));
    assertEquals(1L, current.distribution().get("route").get("technical"));
    assertEquals(1.0 / 3.0, current.noMatchRate(), 1e-9);
    assertEquals((0.9 + 0.7 + 0.5) / 3.0, current.meanConfidence(), 1e-9);
    assertNull(stats.previous());
    assertNull(stats.first());
  }

  @Test
  void rollsTheWindowAndPreservesTheFirstAsBaseline() {
    for (int i = 0; i < 5; i++) {
      recorder.record(req(), decision("billing", 0.8, false), 2);
    }
    DecisionStatsRecorder.SetStats stats = recorder.windowsFor("triage");
    // windowSize=2 → after 5 decisions the first full window is the baseline, one full window is previous, one filling.
    assertEquals(2, stats.first().count());
    assertEquals(2, stats.previous().count());
    assertEquals(1, stats.current().count());
  }

  @Test
  void recordsFailoverEventsAfterAWatermark() {
    recorder.record(req(), failedOver("typesafe", "openrouter"), 100);
    List<DecisionStatsRecorder.FailoverEvent> events = recorder.failoverEventsSince(0L);
    assertEquals(1, events.size());
    assertEquals("typesafe", events.get(0).from());
    assertEquals("openrouter", events.get(0).to());
    assertTrue(recorder.failoverEventsSince(System.currentTimeMillis() + 1000L).isEmpty());
  }

  private static DecisionRequest req() {
    return new DecisionRequest(Json.read("{\"x\":1}"), null, (ObjectNode) Json.read("{}"), Map.of(), "triage", "1");
  }

  private static DecisionOutcome decision(String choice, double confidence, boolean noMatch) {
    ObjectNode answers = Json.object();
    ObjectNode route = answers.putObject("route");
    route.put("choice", choice);
    route.put("confidence", confidence);
    route.putObject("derived").put("isNoMatch", noMatch);
    return new DecisionOutcome(answers, DecisionAttributes.builder().provider("openrouter").build());
  }

  private static DecisionOutcome failedOver(String from, String to) {
    DecisionAttributes attributes = DecisionAttributes.builder().provider(to).failedOverFrom(List.of(from)).build();
    return new DecisionOutcome(Json.object(), attributes);
  }
}
