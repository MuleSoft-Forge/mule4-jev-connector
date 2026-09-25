package com.mulesoft.connectors.jev.internal.policy;

import com.mulesoft.connectors.jev.internal.util.Json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyEvaluatorTest {

  private static final String CHOICE_POLICY = "{\"team\":{\"minProbability\":0.55,\"minMargin\":0.15,\"onNoMatch\":\"REVIEW\"}}";

  private static ObjectNode choice(double top, double margin, boolean noMatch) {
    String json = "{\"team\":{\"type\":\"choice\",\"choice\":\"billing\",\"probabilities\":{\"billing\":" + top
        + ",\"technical\":" + (top - margin) + "},\"derived\":{\"margin\":" + margin + ",\"isNoMatch\":" + noMatch
        + "}}}";
    return (ObjectNode) Json.read(json);
  }

  @Test
  void choiceThatClearsThresholdsIsAcceptedAndSetsRouteKey() {
    ObjectNode result = PolicyEvaluator.evaluate(choice(0.7, 0.5, false), Json.read(CHOICE_POLICY));
    assertEquals("ACCEPT", result.get("action").asText());
    assertEquals("billing", result.get("routeKey").asText());
    assertTrue(result.get("reasons").isEmpty());
  }

  @Test
  void choiceBelowMarginIsReviewedWithReason() {
    ObjectNode result = PolicyEvaluator.evaluate(choice(0.6, 0.05, false), Json.read(CHOICE_POLICY));
    assertEquals("REVIEW", result.get("action").asText());
    boolean mentionsMargin = false;
    for (JsonNode reason : result.get("reasons")) {
      mentionsMargin |= reason.asText().contains("margin");
    }
    assertTrue(mentionsMargin, "expected a margin reason in " + result.get("reasons"));
  }

  @Test
  void noMatchChoiceFollowsOnNoMatch() {
    ObjectNode result = PolicyEvaluator.evaluate(choice(0.9, 0.8, true), Json.read(CHOICE_POLICY));
    assertEquals("REVIEW", result.get("action").asText());
  }

  @Test
  void noulAcceptReviewReject() {
    JsonNode policy = Json.read("{\"urgent\":{\"acceptAbove\":0.7,\"rejectBelow\":0.3}}");
    assertEquals("ACCEPT", action(noul(0.8), policy));
    assertEquals("REVIEW", action(noul(0.5), policy));
    assertEquals("REJECT", action(noul(0.2), policy));
  }

  @Test
  void scoreLevelsDriveAction() {
    JsonNode policy = Json.read("{\"sentiment\":{\"acceptLevels\":[\"3\",\"4\",\"5\"],\"reviewLevels\":[\"2\"]}}");
    assertEquals("ACCEPT", action(score(4), policy));
    assertEquals("REVIEW", action(score(2), policy));
    assertEquals("REJECT", action(score(1), policy));
  }

  @Test
  void mostCautiousOutcomeWinsAcrossQuestions() {
    ObjectNode decision = choice(0.7, 0.5, false);
    decision.set("urgent", Json.read("{\"type\":\"noul\",\"probability\":0.1}").deepCopy());
    JsonNode policy = Json.read(
        "{\"team\":{\"minProbability\":0.55,\"minMargin\":0.15},\"urgent\":{\"acceptAbove\":0.7,\"rejectBelow\":0.3}}");
    ObjectNode result = PolicyEvaluator.evaluate(decision, policy);
    assertEquals("REJECT", result.get("action").asText());
  }

  @Test
  void canonicalEvaluatePayloadIsUnwrappedToItsAnswers() {
    ObjectNode payload = Json.object();
    payload.put("model", "mock");
    payload.set("answers", choice(0.6, 0.05, false));
    // Without unwrapping, the {model, answers} wrapper would judge no questions and default to ACCEPT.
    assertEquals("REVIEW", PolicyEvaluator.evaluate(payload, Json.read(CHOICE_POLICY)).get("action").asText());
  }

  @Test
  void singleAnswerIsWrappedAndJudged() {
    JsonNode decision = Json.read("{\"type\":\"noul\",\"probability\":0.1}");
    JsonNode policy = Json.read("{\"result\":{\"acceptAbove\":0.7,\"rejectBelow\":0.3}}");
    assertEquals("REJECT", PolicyEvaluator.evaluate(decision, policy).get("action").asText());
  }

  private static ObjectNode noul(double probability) {
    return (ObjectNode) Json.read("{\"urgent\":{\"type\":\"noul\",\"probability\":" + probability + "}}");
  }

  private static ObjectNode score(int level) {
    return (ObjectNode) Json
        .read("{\"sentiment\":{\"type\":\"score\",\"score\":\"" + level + "\",\"derived\":{\"level\":" + level + "}}}");
  }

  private static String action(JsonNode decision, JsonNode policy) {
    return PolicyEvaluator.evaluate(decision, policy).get("action").asText();
  }
}
