package com.mulesoft.connectors.jev.internal.operation;

import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.error.Throws;
import org.mule.sdk.api.annotation.metadata.MetadataKeyId;
import org.mule.sdk.api.annotation.metadata.OutputResolver;
import org.mule.sdk.api.annotation.param.Config;
import org.mule.sdk.api.annotation.param.Connection;
import org.mule.sdk.api.annotation.param.Content;
import org.mule.sdk.api.annotation.param.MediaType;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.ParameterGroup;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.exception.ModuleException;
import org.mule.sdk.api.runtime.operation.Result;
import org.mule.sdk.api.runtime.process.CompletionCallback;

import com.mulesoft.connectors.jev.api.attributes.DecisionAttributes;
import com.mulesoft.connectors.jev.internal.config.JevConfiguration;
import com.mulesoft.connectors.jev.internal.connection.JevConnection;
import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.engine.DecisionContext;
import com.mulesoft.connectors.jev.internal.engine.DecisionEngine;
import com.mulesoft.connectors.jev.internal.engine.DecisionOutcome;
import com.mulesoft.connectors.jev.internal.error.DecisionErrorTypeProvider;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.metadata.DecisionOutputResolver;
import com.mulesoft.connectors.jev.internal.metadata.QuestionSetTypeKeysResolver;
import com.mulesoft.connectors.jev.internal.questionset.QuestionSet;
import com.mulesoft.connectors.jev.internal.questionset.QuestionSetLoader;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The billed decision operations. {@code evaluate} sends a full question set; {@code ask-noul}, {@code choose} and
 * {@code score} are single-question shortcuts; {@code select-candidate} turns upstream rows into a dynamic Choice.
 * Every operation is non-blocking (a {@link CompletionCallback} plus {@code sendAsync}), builds a canonical
 * {@link DecisionRequest}, hands it to the {@link DecisionEngine}, and streams JSON back with
 * {@link DecisionAttributes}. Retries run on a runtime scheduler, so no I/O thread ever sleeps.
 */
public class DecisionOperations {

  private static final String NO_MATCH_OPTION = "noMatchOption";
  private static final String SHORTCUT_KEY = "result";
  private static final String CANDIDATE_KEY = "candidate";
  private static final String CANDIDATE_NO_MATCH = "__no_match__";
  private static final int MAX_CANDIDATES = 254;

  /**
   * Evaluates one decision against the connected route. Supply the questions inline ({@code questions}) or by
   * referencing a classpath question-set file ({@code questionSet}); exactly one is required. The output is the answers
   * object (each answer enriched with a {@code derived} block); {@code attributes} carry provider, usage, cost, timing
   * and a {@code traceEntry}.
   */
  @Alias("evaluate")
  @DisplayName("[Decide] Evaluate")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @OutputResolver(output = DecisionOutputResolver.class)
  @Throws(DecisionErrorTypeProvider.class)
  public void evaluate(@Config JevConfiguration config, @Connection JevConnection connection,
      @Content InputStream state, @Optional @Content(primary = false) @DisplayName("Questions") InputStream questions,
      @Optional @DisplayName("Question set") @MetadataKeyId(QuestionSetTypeKeysResolver.class) String questionSet,
      @Optional String questionSetId, @Optional String questionSetVersion,
      @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    DecisionRequest request;
    try {
      JsonNode stateNode = Json.read(state);
      Resolved resolved = resolveQuestions(config, questions, questionSet, questionSetId, questionSetVersion);
      request = new DecisionRequest(stateNode, options.getModelOverride(), resolved.questions, resolved.noMatchOptions,
          resolved.questionSetId, resolved.questionSetVersion);
    } catch (ModuleException e) {
      callback.error(e);
      return;
    } catch (RuntimeException e) {
      callback.error(
          new ModuleException("Could not parse the decision input as JSON", JevErrorType.INVALID_QUESTION_SET, e));
      return;
    }
    run(config, connection, request, options, callback, DecisionOperations::wrapAnswers);
  }

  /** Wraps the enriched answers as the §7 canonical payload {@code {model, answers}} for the full evaluate op. */
  private static ObjectNode wrapAnswers(DecisionOutcome outcome) {
    ObjectNode payload = Json.object();
    if (outcome.attributes().getModel() == null) {
      payload.putNull("model");
    } else {
      payload.put("model", outcome.attributes().getModel());
    }
    payload.set("answers", outcome.payload());
    return payload;
  }

  /**
   * Asks a single yes/no (Noul) question and returns just that answer, so a flow reads {@code payload.noul} and
   * {@code payload.probability} directly.
   */
  @Alias("ask-noul")
  @DisplayName("[Decide] Ask Yes/No")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @Throws(DecisionErrorTypeProvider.class)
  public void askNoul(@Config JevConfiguration config, @Connection JevConnection connection, @Content InputStream state,
      @DisplayName("Instructions") String instructions, @Optional String criteriaTrue, @Optional String criteriaFalse,
      @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    ObjectNode question = Json.object();
    question.put("type", "noul");
    question.put("instructions", instructions);
    if (criteriaTrue != null) {
      question.put("criteriaTrue", criteriaTrue);
    }
    if (criteriaFalse != null) {
      question.put("criteriaFalse", criteriaFalse);
    }
    runShortcut(config, connection, state, question, new HashMap<>(), options, callback);
  }

  /**
   * Asks a single Choice question over a fixed set of options and returns just that answer, so a flow reads
   * {@code payload.choice} and {@code payload.derived}.
   */
  @Alias("choose")
  @DisplayName("[Decide] Choose")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @Throws(DecisionErrorTypeProvider.class)
  public void choose(@Config JevConfiguration config, @Connection JevConnection connection, @Content InputStream state,
      @DisplayName("Instructions") String instructions, @DisplayName("Options") Map<String, String> chooseOptions,
      @Optional String noMatchOption, @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    if (chooseOptions == null || chooseOptions.isEmpty()) {
      callback.error(new ModuleException("choose requires at least one option", JevErrorType.INVALID_QUESTION_SET));
      return;
    }
    ObjectNode question = Json.object();
    question.put("type", "choice");
    question.put("instructions", instructions);
    ObjectNode optionsNode = question.putObject("options");
    for (Map.Entry<String, String> entry : chooseOptions.entrySet()) {
      optionsNode.put(entry.getKey(), entry.getValue());
    }
    Map<String, String> noMatch = new HashMap<>();
    if (noMatchOption != null && !noMatchOption.isBlank()) {
      if (!optionsNode.has(noMatchOption)) {
        optionsNode.put(noMatchOption, "None of the options apply");
      }
      noMatch.put(SHORTCUT_KEY, noMatchOption);
    }
    runShortcut(config, connection, state, question, noMatch, options, callback);
  }

  /**
   * Asks a single Score question over ordered levels and returns just that answer, so a flow reads
   * {@code payload.score} and {@code payload.derived.level}.
   */
  @Alias("score")
  @DisplayName("[Decide] Score")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @Throws(DecisionErrorTypeProvider.class)
  public void score(@Config JevConfiguration config, @Connection JevConnection connection, @Content InputStream state,
      @DisplayName("Instructions") String instructions, @DisplayName("Levels") List<String> levels,
      @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    if (levels == null || levels.size() < 2) {
      callback.error(new ModuleException("score requires at least two levels", JevErrorType.INVALID_QUESTION_SET));
      return;
    }
    ObjectNode question = Json.object();
    question.put("type", "score");
    question.put("instructions", instructions);
    ArrayNode levelsNode = question.putArray("levels");
    for (String level : levels) {
      levelsNode.add(level);
    }
    runShortcut(config, connection, state, question, new HashMap<>(), options, callback);
  }

  /**
   * Turns a list of upstream candidates (DB rows, Salesforce queues, …) into a dynamic Choice: each candidate becomes
   * an option keyed by {@code idField} and described by {@code labelField} / {@code descriptionField}. The payload
   * names the selected candidate object, its probability and confidence, whether it was a no-match, and the full
   * ranking.
   */
  @Alias("select-candidate")
  @DisplayName("[Select] Candidate")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @Throws(DecisionErrorTypeProvider.class)
  public void selectCandidate(@Config JevConfiguration config, @Connection JevConnection connection,
      @Content InputStream candidates, @Content(primary = false) InputStream query, String idField, String labelField,
      @Optional String descriptionField,
      @Optional(defaultValue = "Which candidate best matches the request?") String instructions,
      @Optional(defaultValue = "true") boolean includeNoMatch,
      @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    List<JsonNode> candidateList;
    ObjectNode question;
    Map<String, String> noMatch = new HashMap<>();
    Map<String, JsonNode> byId = new LinkedHashMap<>();
    try {
      candidateList = readArray(candidates);
      question = buildCandidateQuestion(candidateList, idField, labelField, descriptionField, instructions,
          includeNoMatch, byId, noMatch);
    } catch (ModuleException e) {
      callback.error(e);
      return;
    } catch (RuntimeException e) {
      callback.error(
          new ModuleException("Could not parse candidates as a JSON array", JevErrorType.INVALID_QUESTION_SET, e));
      return;
    }

    JsonNode queryNode;
    try {
      queryNode = Json.read(query);
    } catch (RuntimeException e) {
      callback.error(new ModuleException("Could not parse query as JSON", JevErrorType.INVALID_STATE, e));
      return;
    }

    ObjectNode questions = Json.object();
    questions.set(CANDIDATE_KEY, question);
    DecisionRequest request = new DecisionRequest(queryNode, options.getModelOverride(), questions, noMatch, null,
        null);
    run(config, connection, request, options, callback, outcome -> candidatePayload(outcome, byId));
  }

  /** Shared tail: build the context, run the engine, stream the payload the caller shaped. */
  private void run(JevConfiguration config, JevConnection connection, DecisionRequest request, RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback, Function<DecisionOutcome, ObjectNode> payloadFn) {
    DecisionContext context = new DecisionContext(config.getPricePerMillionInputTokens(),
        options.isIncludeRawResponse(), options.getStep());
    connection.engine().evaluate(connection, request, context).whenComplete((outcome, error) -> {
      if (error != null) {
        callback.error(unwrap(error));
        return;
      }
      byte[] payload = Json.write(payloadFn.apply(outcome)).getBytes(StandardCharsets.UTF_8);
      callback.success(Result.<InputStream, DecisionAttributes>builder().output(new ByteArrayInputStream(payload))
          .attributes(outcome.attributes()).build());
    });
  }

  private void runShortcut(JevConfiguration config, JevConnection connection, InputStream state, ObjectNode question,
      Map<String, String> noMatchOptions, RequestOptions options,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    JsonNode stateNode;
    try {
      stateNode = Json.read(state);
    } catch (RuntimeException e) {
      callback.error(new ModuleException("Could not parse state as JSON", JevErrorType.INVALID_STATE, e));
      return;
    }
    ObjectNode questions = Json.object();
    questions.set(SHORTCUT_KEY, question);
    DecisionRequest request = new DecisionRequest(stateNode, options.getModelOverride(), questions, noMatchOptions,
        null, null);
    run(config, connection, request, options, callback, outcome -> unwrapSingle(outcome));
  }

  /** Lifts the single shortcut answer out of the {@code answers} wrapper so flows read it as the payload. */
  private static ObjectNode unwrapSingle(DecisionOutcome outcome) {
    JsonNode answer = outcome.payload().get(SHORTCUT_KEY);
    return answer instanceof ObjectNode ? (ObjectNode) answer : outcome.payload();
  }

  private ObjectNode buildCandidateQuestion(List<JsonNode> candidates, String idField, String labelField,
      String descriptionField, String instructions, boolean includeNoMatch, Map<String, JsonNode> byId,
      Map<String, String> noMatch) {
    if (candidates.isEmpty()) {
      throw new ModuleException("select-candidate requires at least one candidate", JevErrorType.INVALID_QUESTION_SET);
    }
    if (candidates.size() > MAX_CANDIDATES) {
      throw new ModuleException(
          "select-candidate supports at most " + MAX_CANDIDATES + " candidates; got " + candidates.size(),
          JevErrorType.TOO_MANY_OPTIONS);
    }
    ObjectNode question = Json.object();
    question.put("type", "choice");
    question.put("instructions", instructions);
    ObjectNode optionsNode = question.putObject("options");
    for (JsonNode candidate : candidates) {
      JsonNode idNode = candidate.get(idField);
      String id = idNode != null && !idNode.isNull() ? idNode.asText() : null;
      if (id == null || id.isBlank()) {
        throw new ModuleException("Every candidate needs a non-empty '" + idField + "'",
            JevErrorType.INVALID_QUESTION_SET);
      }
      if (byId.containsKey(id)) {
        throw new ModuleException("Candidate ids must be unique; '" + id + "' is repeated",
            JevErrorType.INVALID_QUESTION_SET);
      }
      byId.put(id, candidate);
      optionsNode.put(id, describe(candidate, labelField, descriptionField));
    }
    if (includeNoMatch) {
      optionsNode.put(CANDIDATE_NO_MATCH, "None of the candidates apply");
      noMatch.put(CANDIDATE_KEY, CANDIDATE_NO_MATCH);
    }
    return question;
  }

  private static String describe(JsonNode candidate, String labelField, String descriptionField) {
    String label = text(candidate, labelField);
    String description = descriptionField == null ? null : text(candidate, descriptionField);
    if (description == null || description.isBlank()) {
      return label;
    }
    return label + " — " + description;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = field == null ? null : node.get(field);
    return value != null && !value.isNull() ? value.asText() : "";
  }

  private static ObjectNode candidatePayload(DecisionOutcome outcome, Map<String, JsonNode> byId) {
    JsonNode answerNode = outcome.payload().get(CANDIDATE_KEY);
    ObjectNode answer = answerNode instanceof ObjectNode ? (ObjectNode) answerNode : Json.object();
    String id = answer.path("choice").asText(null);
    boolean isNoMatch = answer.path("derived").path("isNoMatch").asBoolean(false) || CANDIDATE_NO_MATCH.equals(id);

    ObjectNode payload = Json.object();
    JsonNode selected = id != null && !isNoMatch ? byId.get(id) : null;
    if (selected != null) {
      payload.set("selected", selected.deepCopy());
    } else {
      payload.putNull("selected");
    }
    if (id == null) {
      payload.putNull("id");
    } else {
      payload.put("id", id);
    }
    JsonNode probabilities = answer.get("probabilities");
    double probability = id != null && probabilities != null ? probabilities.path(id).asDouble(0.0) : 0.0;
    payload.put("probability", probability);
    if (answer.hasNonNull("confidence")) {
      payload.put("confidence", answer.get("confidence").asDouble());
    } else {
      payload.putNull("confidence");
    }
    payload.put("isNoMatch", isNoMatch);
    ArrayNode ranking = payload.putArray("ranking");
    if (probabilities != null && probabilities.isObject()) {
      List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
      probabilities.fields().forEachRemaining(entries::add);
      entries
          .sort(Comparator.comparingDouble((Map.Entry<String, JsonNode> e) -> e.getValue().asDouble(0.0)).reversed());
      for (Map.Entry<String, JsonNode> entry : entries) {
        ranking.addObject().put("id", entry.getKey()).put("probability", entry.getValue().asDouble(0.0));
      }
    }
    return payload;
  }

  /**
   * Resolves the question map, its no-match annotations and identity from either the inline {@code questions} content
   * or a referenced question-set file. Exactly one source must be supplied.
   */
  private static Resolved resolveQuestions(JevConfiguration config, InputStream questions, String questionSet,
      String questionSetId, String questionSetVersion) {
    boolean hasInline = questions != null;
    boolean hasFile = questionSet != null && !questionSet.isBlank();
    if (hasInline && hasFile) {
      throw new ModuleException("Supply either 'questions' or 'questionSet', not both",
          JevErrorType.INVALID_QUESTION_SET);
    }
    if (!hasInline && !hasFile) {
      throw new ModuleException("Supply one of 'questions' or 'questionSet'", JevErrorType.INVALID_QUESTION_SET);
    }

    JsonNode questionsNode;
    String id = questionSetId;
    String version = questionSetVersion;
    if (hasFile) {
      QuestionSet set = QuestionSetLoader.load(config.getDefaultQuestionSetsLocation(), questionSet);
      questionsNode = set.questions();
      if (id == null) {
        id = set.id();
      }
      if (version == null) {
        version = set.version();
      }
    } else {
      questionsNode = Json.read(questions);
    }
    if (questionsNode == null || !questionsNode.isObject()) {
      throw new ModuleException("The questions input must be a JSON object keyed by question id",
          JevErrorType.INVALID_QUESTION_SET);
    }
    Resolved resolved = clean((ObjectNode) questionsNode);
    resolved.questionSetId = id;
    resolved.questionSetVersion = version;
    return resolved;
  }

  /** Strips connector-side {@code noMatchOption} annotations from each question, capturing them for {@code derived}. */
  private static Resolved clean(ObjectNode questions) {
    ObjectNode cleaned = Json.object();
    Map<String, String> noMatchOptions = new HashMap<>();
    Iterator<Map.Entry<String, JsonNode>> it = questions.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> entry = it.next();
      JsonNode question = entry.getValue();
      if (!question.isObject()) {
        cleaned.set(entry.getKey(), question);
        continue;
      }
      ObjectNode copy = (ObjectNode) question.deepCopy();
      JsonNode noMatch = copy.remove(NO_MATCH_OPTION);
      if (noMatch != null && noMatch.isTextual()) {
        noMatchOptions.put(entry.getKey(), noMatch.asText());
      }
      cleaned.set(entry.getKey(), copy);
    }
    Resolved resolved = new Resolved();
    resolved.questions = cleaned;
    resolved.noMatchOptions = noMatchOptions;
    return resolved;
  }

  private static List<JsonNode> readArray(InputStream in) {
    JsonNode node = Json.read(in);
    if (node == null || !node.isArray()) {
      throw new ModuleException("candidates must be a JSON array of objects", JevErrorType.INVALID_QUESTION_SET);
    }
    List<JsonNode> list = new ArrayList<>();
    node.forEach(list::add);
    return list;
  }

  private static Throwable unwrap(Throwable error) {
    if (error instanceof CompletionException && error.getCause() != null) {
      return error.getCause();
    }
    return error;
  }

  /** Mutable carrier for the resolved question map and its derived-time annotations. */
  private static final class Resolved {

    private ObjectNode questions;
    private Map<String, String> noMatchOptions;
    private String questionSetId;
    private String questionSetVersion;
  }
}
