package com.mulesoft.connectors.jev.internal.provider;

import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.domain.DecisionResponse;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockAdapterTest {

  private DecisionResponse evaluate(String questionsJson, double defaultNoul) {
    ObjectNode questions = (ObjectNode) Json.read(questionsJson);
    DecisionRequest request = new DecisionRequest(Json.read("{}"), null, questions, Map.of(), null, null);
    return new MockAdapter(defaultNoul, 0L).evaluate(request).join();
  }

  @Test
  void synthesisesNoulAnswer() {
    DecisionResponse response = evaluate("{\"q\":{\"type\":\"noul\",\"instructions\":\"?\"}}", 0.8);
    ObjectNode answer = (ObjectNode) response.answers().get("q");
    assertEquals("noul", answer.get("type").asText());
    assertTrue(answer.get("answer").asBoolean());
    assertEquals(0.8, answer.get("probability").asDouble(), 1e-9);
  }

  @Test
  void synthesisesChoiceAnswerWithDistribution() {
    DecisionResponse response = evaluate("{\"q\":{\"type\":\"choice\",\"criteria\":{\"a\":\"A\",\"b\":\"B\"}}}", 0.5);
    ObjectNode answer = (ObjectNode) response.answers().get("q");
    assertEquals("choice", answer.get("type").asText());
    assertEquals("a", answer.get("choice").asText());
    assertEquals(0.6, answer.get("probabilities").get("a").asDouble(), 1e-9);
  }

  @Test
  void synthesisesScoreAnswerFromLegend() {
    DecisionResponse response = evaluate(
        "{\"q\":{\"type\":\"score\",\"legend\":{\"1\":\"low\",\"2\":\"mid\",\"3\":\"high\"}}}", 0.5);
    ObjectNode answer = (ObjectNode) response.answers().get("q");
    assertEquals("score", answer.get("type").asText());
    assertEquals("1", answer.get("score").asText());
    assertTrue(answer.has("legend"));
  }

  @Test
  void reportsMockRoute() {
    assertEquals("mock", new MockAdapter(0.5, 0L).routeName());
  }
}
