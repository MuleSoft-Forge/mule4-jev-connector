package com.mulesoft.connectors.jev.internal.domain;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Adds connector-computed values under each answer's {@code derived} object, leaving the provider's
 * own fields untouched.
 *
 * <ul>
 * <li>Choice: {@code margin} (top probability minus runner-up), {@code runnerUp}, {@code isNoMatch}.
 * <li>Score: {@code level} (the most probable level, not {@code round(score)}), {@code levelLabel}.
 * <li>Noul: nothing derived.
 * </ul>
 */
public final class DerivedComputer {

  private DerivedComputer() {}

  /** Enriches every answer in place. {@code noMatchOptions} maps question id to its no-match key. */
  public static void enrich(ObjectNode answers, Map<String, String> noMatchOptions) {
    if (answers == null) {
      return;
    }
    Iterator<Map.Entry<String, JsonNode>> it = answers.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> entry = it.next();
      JsonNode answer = entry.getValue();
      if (!answer.isObject()) {
        continue;
      }
      ObjectNode obj = (ObjectNode) answer;
      String type = obj.path("type").asText("");
      if ("choice".equals(type)) {
        enrichChoice(entry.getKey(), obj, noMatchOptions);
      } else if ("score".equals(type)) {
        enrichScore(obj);
      }
    }
  }

  private static void enrichChoice(String questionId, ObjectNode answer, Map<String, String> noMatchOptions) {
    JsonNode probabilities = answer.get("probabilities");
    ObjectNode derived = answer.putObject("derived");

    String topKey = null;
    double top = Double.NEGATIVE_INFINITY;
    String secondKey = null;
    double second = Double.NEGATIVE_INFINITY;

    if (probabilities != null && probabilities.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> it = probabilities.fields();
      while (it.hasNext()) {
        Map.Entry<String, JsonNode> e = it.next();
        double p = e.getValue().asDouble(0.0);
        if (p > top) {
          second = top;
          secondKey = topKey;
          top = p;
          topKey = e.getKey();
        } else if (p > second) {
          second = p;
          secondKey = e.getKey();
        }
      }
    }

    double topValue = top == Double.NEGATIVE_INFINITY ? 0.0 : top;
    double secondValue = second == Double.NEGATIVE_INFINITY ? 0.0 : second;
    derived.put("margin", topValue - secondValue);
    if (secondKey == null) {
      derived.putNull("runnerUp");
    } else {
      derived.put("runnerUp", secondKey);
    }

    String noMatch = noMatchOptions.get(questionId);
    String choice = answer.path("choice").asText(null);
    derived.put("isNoMatch", noMatch != null && noMatch.equals(choice));
  }

  private static void enrichScore(ObjectNode answer) {
    JsonNode probabilities = answer.get("probabilities");
    JsonNode legend = answer.get("legend");
    ObjectNode derived = answer.putObject("derived");

    String topKey = null;
    double top = Double.NEGATIVE_INFINITY;
    if (probabilities != null && probabilities.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> it = probabilities.fields();
      while (it.hasNext()) {
        Map.Entry<String, JsonNode> e = it.next();
        double p = e.getValue().asDouble(0.0);
        if (p > top) {
          top = p;
          topKey = e.getKey();
        }
      }
    }

    if (topKey == null) {
      derived.putNull("level");
      derived.putNull("levelLabel");
      return;
    }

    try {
      derived.put("level", Integer.parseInt(topKey));
    } catch (NumberFormatException e) {
      derived.put("level", topKey);
    }
    if (legend != null && legend.isObject() && legend.has(topKey)) {
      derived.put("levelLabel", legend.get(topKey).asText());
    } else {
      derived.putNull("levelLabel");
    }
  }
}
