package com.mulesoft.connectors.jev.internal.domain;

import com.mulesoft.connectors.jev.internal.util.Json;

import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedComputerTest {

  @Test
  void enrichesChoiceWithMarginRunnerUpAndNoMatch() {
    ObjectNode answers = (ObjectNode) Json
        .read("{\"q\":{\"type\":\"choice\",\"choice\":\"a\",\"probabilities\":{\"a\":0.7,\"b\":0.2,\"c\":0.1}}}");

    DerivedComputer.enrich(answers, Map.of("q", "c"));

    ObjectNode derived = (ObjectNode) answers.get("q").get("derived");
    assertEquals(0.5, derived.get("margin").asDouble(), 1e-9);
    assertEquals("b", derived.get("runnerUp").asText());
    assertFalse(derived.get("isNoMatch").asBoolean());
  }

  @Test
  void flagsNoMatchWhenChosenOptionIsTheNoMatchKey() {
    ObjectNode answers = (ObjectNode) Json
        .read("{\"q\":{\"type\":\"choice\",\"choice\":\"none\",\"probabilities\":{\"none\":0.9,\"a\":0.1}}}");

    DerivedComputer.enrich(answers, Map.of("q", "none"));

    assertTrue(answers.get("q").get("derived").get("isNoMatch").asBoolean());
  }

  @Test
  void enrichesScoreWithMostProbableLevelAndLabel() {
    ObjectNode answers = (ObjectNode) Json
        .read("{\"q\":{\"type\":\"score\",\"probabilities\":{\"1\":0.1,\"2\":0.2,\"3\":0.7},"
            + "\"legend\":{\"1\":\"low\",\"2\":\"mid\",\"3\":\"high\"}}}");

    DerivedComputer.enrich(answers, Map.of());

    ObjectNode derived = (ObjectNode) answers.get("q").get("derived");
    assertEquals(3, derived.get("level").asInt());
    assertEquals("high", derived.get("levelLabel").asText());
  }

  @Test
  void leavesNoulUntouched() {
    ObjectNode answers = (ObjectNode) Json.read("{\"q\":{\"type\":\"noul\",\"noul\":0.8}}");
    DerivedComputer.enrich(answers, Map.of());
    assertFalse(answers.get("q").has("derived"));
  }
}
