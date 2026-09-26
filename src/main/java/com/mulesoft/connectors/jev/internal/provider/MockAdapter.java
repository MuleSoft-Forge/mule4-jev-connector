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
 * It reads options and levels only from TypeSafe's {@code criteria} field and answers in TypeSafe's shapes (Noul
 * {@code noul}; Score {@code score}, 0-based {@code legend}/{@code probabilities}), so downstream derivation and
 * DataSense behave exactly as with a real route and a question the real API would reject yields no options here.
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

  @Override
  public CompletableFuture<List<String>> listModels() {
    return CompletableFuture.completedFuture(List.of("mock", "mock-latest"));
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
    answer.put("noul", defaultNoul);
    return answer;
  }

  private ObjectNode choiceAnswer(JsonNode question) {
    List<String> options = new ArrayList<>();
    JsonNode criteria = question.get("criteria");
    if (criteria != null && criteria.isObject()) {
      criteria.fieldNames().forEachRemaining(options::add);
    }
    ObjectNode answer = Json.object();
    answer.put("type", "choice");
    ObjectNode probabilities = answer.putObject("probabilities");
    if (options.isEmpty()) {
      answer.putNull("choice");
      return answer;
    }
    double[] weights = distribution(options.size());
    for (int i = 0; i < options.size(); i++) {
      probabilities.put(options.get(i), weights[i]);
    }
    answer.put("choice", options.get(0));
    answer.put("confidence", weights[0]);
    return answer;
  }

  private ObjectNode scoreAnswer(JsonNode question) {
    JsonNode criteria = question.get("criteria");
    int levels = criteria != null && criteria.isArray() ? criteria.size() : 0;
    ObjectNode answer = Json.object();
    answer.put("type", "score");
    ObjectNode legend = answer.putObject("legend");
    ObjectNode probabilities = answer.putObject("probabilities");
    if (levels == 0) {
      answer.putNull("score");
      return answer;
    }
    double[] weights = distribution(levels);
    double expected = 0.0;
    for (int i = 0; i < levels; i++) {
      legend.put(Integer.toString(i), criteria.get(i).asText());
      probabilities.put(Integer.toString(i), weights[i]);
      expected += i * weights[i];
    }
    answer.put("score", expected);
    answer.put("confidence", weights[0]);
    return answer;
  }

  /** Concentrates 0.6 on the first of {@code size} slots, splitting the remainder evenly across the rest. */
  private static double[] distribution(int size) {
    double[] weights = new double[size];
    if (size == 1) {
      weights[0] = 1.0;
      return weights;
    }
    double tail = 0.4 / (size - 1);
    for (int i = 0; i < size; i++) {
      weights[i] = i == 0 ? 0.6 : tail;
    }
    return weights;
  }
}
