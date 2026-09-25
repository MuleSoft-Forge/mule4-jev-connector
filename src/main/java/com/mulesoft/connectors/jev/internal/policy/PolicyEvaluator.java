package com.mulesoft.connectors.jev.internal.policy;

import com.mulesoft.connectors.jev.internal.util.Json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Turns a decision (the answers of one {@code evaluate}, or a single shortcut answer) plus a policy into an action.
 * Pure and side-effect free, so it is unit-tested directly and the operation stays a thin adapter.
 *
 * <p>
 * Per question id the policy is type-specific — Choice {@code {minProbability, minConfidence, minMargin, onNoMatch}},
 * Noul {@code {acceptAbove, rejectBelow}}, Score {@code {acceptLevels, reviewLevels, minConfidence}}. The overall
 * action is the most cautious outcome across every judged question: {@code REJECT} beats {@code REVIEW} beats
 * {@code ACCEPT}.
 */
public final class PolicyEvaluator {

  /** Ordered by caution; {@link #maxSeverity} keeps the worst. */
  public enum Action {
    ACCEPT, REVIEW, REJECT
  }

  private PolicyEvaluator() {
  }

  /**
   * Evaluates {@code decision} against {@code policy}.
   *
   * @param decision
   *          either an answers map (question id → answer) or a single answer object (treated as the one question
   *          {@code result}).
   * @param policy
   *          a map of question id → type-specific thresholds.
   * @return the policy result payload {@code {action, routeKey, reasons, perQuestion}}.
   */
  public static ObjectNode evaluate(JsonNode decision, JsonNode policy) {
    ObjectNode answers = asAnswers(decision);
    ObjectNode perQuestion = Json.object();
    List<String> reasons = new ArrayList<>();
    Action overall = Action.ACCEPT;
    String routeKey = null;

    Iterator<Map.Entry<String, JsonNode>> it = answers.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> entry = it.next();
      String id = entry.getKey();
      JsonNode answer = entry.getValue();
      if (!answer.isObject()) {
        continue;
      }
      JsonNode rule = policy == null ? null : policy.get(id);
      QuestionOutcome outcome = judge(id, (ObjectNode) answer, rule);
      overall = maxSeverity(overall, outcome.action);
      reasons.addAll(outcome.reasons);

      ObjectNode pq = perQuestion.putObject(id);
      pq.put("action", outcome.action.name());
      if (!outcome.reasons.isEmpty()) {
        ArrayNode r = pq.putArray("reasons");
        outcome.reasons.forEach(r::add);
      }
      if (routeKey == null && "choice".equals(answer.path("type").asText(""))) {
        routeKey = answer.path("choice").asText(null);
      }
    }

    ObjectNode result = Json.object();
    result.put("action", overall.name());
    if (routeKey == null) {
      result.putNull("routeKey");
    } else {
      result.put("routeKey", routeKey);
    }
    ArrayNode reasonsNode = result.putArray("reasons");
    reasons.forEach(reasonsNode::add);
    result.set("perQuestion", perQuestion);
    return result;
  }

  private static QuestionOutcome judge(String id, ObjectNode answer, JsonNode rule) {
    String type = answer.path("type").asText("");
    switch (type) {
      case "choice" :
        return judgeChoice(id, answer, rule);
      case "noul" :
        return judgeNoul(id, answer, rule);
      case "score" :
        return judgeScore(id, answer, rule);
      default :
        return new QuestionOutcome(Action.ACCEPT);
    }
  }

  private static QuestionOutcome judgeChoice(String id, ObjectNode answer, JsonNode rule) {
    QuestionOutcome outcome = new QuestionOutcome(Action.ACCEPT);
    if (rule == null || !rule.isObject()) {
      return outcome;
    }
    boolean isNoMatch = answer.path("derived").path("isNoMatch").asBoolean(false);
    if (isNoMatch) {
      Action onNoMatch = parseAction(rule.path("onNoMatch").asText("REVIEW"));
      outcome.action = onNoMatch;
      if (onNoMatch != Action.ACCEPT) {
        outcome.reasons.add(id + ": no-match option selected");
      }
      return outcome;
    }
    String choice = answer.path("choice").asText(null);
    double probability = choice == null ? 0.0 : answer.path("probabilities").path(choice).asDouble(0.0);
    double margin = answer.path("derived").path("margin").asDouble(0.0);

    checkMin(outcome, id, "probability", probability, rule.get("minProbability"));
    checkMin(outcome, id, "margin", margin, rule.get("minMargin"));
    if (answer.hasNonNull("confidence")) {
      checkMin(outcome, id, "confidence", answer.get("confidence").asDouble(), rule.get("minConfidence"));
    }
    return outcome;
  }

  private static QuestionOutcome judgeNoul(String id, ObjectNode answer, JsonNode rule) {
    QuestionOutcome outcome = new QuestionOutcome(Action.ACCEPT);
    if (rule == null || !rule.isObject()) {
      return outcome;
    }
    double probability = answer.path("probability").asDouble(0.0);
    double acceptAbove = rule.path("acceptAbove").asDouble(Double.NaN);
    double rejectBelow = rule.path("rejectBelow").asDouble(Double.NaN);
    if (!Double.isNaN(rejectBelow) && probability < rejectBelow) {
      outcome.action = Action.REJECT;
      outcome.reasons.add(id + ": probability " + round(probability) + " < " + round(rejectBelow));
    } else if (!Double.isNaN(acceptAbove) && probability < acceptAbove) {
      outcome.action = Action.REVIEW;
      outcome.reasons.add(id + ": probability " + round(probability) + " < " + round(acceptAbove));
    }
    return outcome;
  }

  private static QuestionOutcome judgeScore(String id, ObjectNode answer, JsonNode rule) {
    QuestionOutcome outcome = new QuestionOutcome(Action.ACCEPT);
    if (rule == null || !rule.isObject()) {
      return outcome;
    }
    String level = answer.path("derived").path("level").asText(null);
    if (level == null || "null".equals(level)) {
      level = answer.path("score").asText(null);
    }
    boolean inAccept = contains(rule.get("acceptLevels"), level);
    boolean inReview = contains(rule.get("reviewLevels"), level);
    if (inAccept) {
      outcome.action = Action.ACCEPT;
    } else if (inReview) {
      outcome.action = Action.REVIEW;
      outcome.reasons.add(id + ": level " + level + " needs review");
    } else {
      outcome.action = Action.REJECT;
      outcome.reasons.add(id + ": level " + level + " not accepted");
    }
    if (answer.hasNonNull("confidence") && rule.hasNonNull("minConfidence")
        && answer.get("confidence").asDouble() < rule.get("minConfidence").asDouble()
        && outcome.action == Action.ACCEPT) {
      outcome.action = Action.REVIEW;
      outcome.reasons.add(id + ": confidence " + round(answer.get("confidence").asDouble()) + " < "
          + round(rule.get("minConfidence").asDouble()));
    }
    return outcome;
  }

  private static void checkMin(QuestionOutcome outcome, String id, String label, double value, JsonNode threshold) {
    if (threshold == null || !threshold.isNumber()) {
      return;
    }
    double min = threshold.asDouble();
    if (value < min) {
      outcome.action = maxSeverity(outcome.action, Action.REVIEW);
      outcome.reasons.add(id + ": " + label + " " + round(value) + " < " + round(min));
    }
  }

  private static boolean contains(JsonNode array, String value) {
    if (array == null || !array.isArray() || value == null) {
      return false;
    }
    for (JsonNode node : array) {
      if (value.equals(node.asText())) {
        return true;
      }
    }
    return false;
  }

  private static Action maxSeverity(Action a, Action b) {
    return a.ordinal() >= b.ordinal() ? a : b;
  }

  private static Action parseAction(String value) {
    try {
      return Action.valueOf(value.trim().toUpperCase());
    } catch (RuntimeException e) {
      return Action.REVIEW;
    }
  }

  private static ObjectNode asAnswers(JsonNode decision) {
    if (decision != null && decision.isObject() && decision.has("type")) {
      ObjectNode wrapper = Json.object();
      wrapper.set("result", decision);
      return wrapper;
    }
    // The canonical evaluate payload is {model, answers}; judge the answers map, not the wrapper.
    if (decision != null && decision.isObject() && decision.path("answers").isObject()) {
      return (ObjectNode) decision.get("answers");
    }
    if (decision instanceof ObjectNode) {
      return (ObjectNode) decision;
    }
    return Json.object();
  }

  private static String round(double value) {
    return String.valueOf(Math.round(value * 1000.0) / 1000.0);
  }

  /** One question's verdict as the evaluator accumulates it. */
  private static final class QuestionOutcome {

    private Action action;
    private final List<String> reasons = new ArrayList<>();

    QuestionOutcome(Action action) {
      this.action = action;
    }
  }
}
