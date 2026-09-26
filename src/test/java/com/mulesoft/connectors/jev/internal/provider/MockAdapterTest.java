package com.mulesoft.connectors.jev.internal.provider;

import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.domain.DecisionResponse;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockAdapterTest {

  private DecisionResponse evaluate(String questionsJson, double defaultNoul) {
    ObjectNode questions = (ObjectNode) Json.read(questionsJson);
    DecisionRequest request = new DecisionRequest(Json.read("{}"), null, questions, Map.of(), null, null);
    return new MockAdapter(defaultNoul, 0L).evaluate(request).join();
  }

  @Test
  void synthesisesNoulAnswerInTypeSafeShape() {
    DecisionResponse response = evaluate("{\"q\":{\"type\":\"noul\",\"instructions\":\"?\"}}", 0.8);
    ObjectNode answer = (ObjectNode) response.answers().get("q");
    assertEquals("noul", answer.get("type").asText());
    assertEquals(0.8, answer.get("noul").asDouble(), 1e-9);
    assertFalse(answer.has("probability"));
  }

  @Test
  void synthesisesChoiceAnswerWithDistribution() {
    DecisionResponse response = evaluate("{\"q\":{\"type\":\"choice\",\"criteria\":{\"a\":\"A\",\"b\":\"B\"}}}", 0.5);
    ObjectNode answer = (ObjectNode) response.answers().get("q");
    assertEquals("choice", answer.get("type").asText());
    assertEquals("a", answer.get("choice").asText());
    assertEquals(0.6, answer.get("probabilities").get("a").asDouble(), 1e-9);
    assertTrue(answer.has("confidence"));
  }

  @Test
  void synthesisesScoreAnswerWithZeroBasedLegend() {
    DecisionResponse response = evaluate("{\"q\":{\"type\":\"score\",\"criteria\":[\"low\",\"mid\",\"high\"]}}", 0.5);
    ObjectNode answer = (ObjectNode) response.answers().get("q");
    assertEquals("score", answer.get("type").asText());
    assertEquals("low", answer.get("legend").get("0").asText());
    assertEquals("high", answer.get("legend").get("2").asText());
    assertEquals(0.6, answer.get("probabilities").get("0").asDouble(), 1e-9);
    assertEquals(0.6, answer.get("score").asDouble(), 1e-9);
  }

  @Test
  void ignoresFieldNamesTypeSafeRejects() {
    DecisionResponse response = evaluate(
        "{\"c\":{\"type\":\"choice\",\"options\":{\"a\":\"A\"}},\"s\":{\"type\":\"score\",\"levels\":[\"x\",\"y\"]}}",
        0.5);
    assertTrue(response.answers().get("c").get("choice").isNull());
    assertTrue(response.answers().get("s").get("score").isNull());
  }

  @Test
  void reportsMockRoute() {
    assertEquals("mock", new MockAdapter(0.5, 0L).routeName());
  }
}
