package com.mulesoft.connectors.jev.internal.provider;

import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.domain.DecisionResponse;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * In-process adapter used by the {@code mock} route. It never leaves the runtime and needs no credentials, so tests and
 * the demo app can run without keys. It synthesises a valid, type-appropriate answer for every question in the request:
 * Noul answers report {@code defaultNoul}; Choice and Score answers concentrate probability on the first option/level.
 * The shapes match the canonical wire contract so downstream derivation and DataSense behave exactly as with a real
 * route.
 */
public class MockAdapter implements ProviderAdapter {

  private final double defaultNoul;
  private final long latencyMs;

  public MockAdapter(double defaultNoul, long latencyMs) {
    this.defaultNoul = defaultNoul;
    this.latencyMs = latencyMs;
  }

  public double defaultNoul() {
    return defaultNoul;
  }

  public long latencyMs() {
    return latencyMs;
  }

  @Override
  public String routeName() {
    return "mock";
  }

  @Override
  public Capabilities capabilities() {
    return Capabilities.full(false);
  }

  @Override
  public CompletableFuture<DecisionResponse> evaluate(DecisionRequest request) {
    ObjectNode answers = Json.object();
    ObjectNode questions = request.questions();
    if (questions != null) {
      Iterator<Map.Entry<String, JsonNode>> it = questions.fields();
      while (it.hasNext()) {
        Map.Entry<String, JsonNode> entry = it.next();
        answers.set(entry.getKey(), answerFor(entry.getValue()));
      }
    }

    DecisionResponse response = DecisionResponse.builder().model("mock").requestedModel(request.requestedModel())
        .answers(answers).rawBody(Json.write(answers)).build();
    return CompletableFuture.completedFuture(response);
  }

  private ObjectNode answerFor(JsonNode question) {
    String type = question.path("type").asText("noul");
    switch (type) {
      case "choice" :
        return choiceAnswer(question);
      case "score" :
        return scoreAnswer(question);
      default :
        return noulAnswer();
    }
  }

  private ObjectNode noulAnswer() {
    ObjectNode answer = Json.object();
    answer.put("type", "noul");
    answer.put("answer", defaultNoul >= 0.5);
    answer.put("probability", defaultNoul);
    return answer;
  }

  private ObjectNode choiceAnswer(JsonNode question) {
    List<String> options = optionKeys(question);
    ObjectNode answer = Json.object();
    answer.put("type", "choice");
    ObjectNode probabilities = answer.putObject("probabilities");
    if (options.isEmpty()) {
      answer.putNull("choice");
      return answer;
    }
    distribute(probabilities, options);
    answer.put("choice", options.get(0));
    return answer;
  }

  private ObjectNode scoreAnswer(JsonNode question) {
    List<String> levels = levelKeys(question);
    ObjectNode answer = Json.object();
    answer.put("type", "score");
    JsonNode legend = question.get("legend");
    if (legend != null && legend.isObject()) {
      answer.set("legend", legend.deepCopy());
    }
    ObjectNode probabilities = answer.putObject("probabilities");
    if (levels.isEmpty()) {
      answer.putNull("score");
      return answer;
    }
    distribute(probabilities, levels);
    answer.put("score", levels.get(0));
    return answer;
  }

  /** Concentrates 0.6 on the first key, splitting the remainder evenly across the rest. */
  private static void distribute(ObjectNode probabilities, List<String> keys) {
    if (keys.size() == 1) {
      probabilities.put(keys.get(0), 1.0);
      return;
    }
    double head = 0.6;
    double tail = (1.0 - head) / (keys.size() - 1);
    for (int i = 0; i < keys.size(); i++) {
      probabilities.put(keys.get(i), i == 0 ? head : tail);
    }
  }

  private static List<String> optionKeys(JsonNode question) {
    List<String> keys = new ArrayList<>();
    JsonNode options = question.get("options");
    if (options == null) {
      options = question.get("criteria");
    }
    if (options != null && options.isObject()) {
      options.fieldNames().forEachRemaining(keys::add);
    } else if (options != null && options.isArray()) {
      for (JsonNode option : options) {
        if (option.isTextual()) {
          keys.add(option.asText());
        } else if (option.isObject()) {
          JsonNode id = option.has("id") ? option.get("id") : option.get("key");
          keys.add(id != null ? id.asText() : option.path("label").asText());
        }
      }
    }
    return keys;
  }

  private static List<String> levelKeys(JsonNode question) {
    List<String> keys = new ArrayList<>();
    JsonNode legend = question.get("legend");
    if (legend != null && legend.isObject()) {
      legend.fieldNames().forEachRemaining(keys::add);
      return keys;
    }
    JsonNode levels = question.get("levels");
    if (levels != null && levels.isNumber()) {
      for (int i = 1; i <= levels.asInt(); i++) {
        keys.add(Integer.toString(i));
      }
    } else if (levels != null && levels.isArray()) {
      for (JsonNode level : levels) {
        keys.add(level.asText());
      }
    }
    return keys;
  }
}
