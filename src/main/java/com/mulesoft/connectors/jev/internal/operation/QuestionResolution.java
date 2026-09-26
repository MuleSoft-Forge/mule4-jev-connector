package com.mulesoft.connectors.jev.internal.operation;

import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.jev.internal.config.JevConfiguration;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.questionset.QuestionSet;
import com.mulesoft.connectors.jev.internal.questionset.QuestionSetLoader;
import com.mulesoft.connectors.jev.internal.util.Json;
import com.mulesoft.connectors.jev.internal.validation.QuestionSetValidator;
import com.mulesoft.connectors.jev.internal.validation.ValidationResult;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Resolves the question map, its no-match annotations and its identity from either inline {@code questions} content or
 * a referenced classpath question-set file, and strips the connector-side {@code noMatchOption} annotations so they
 * drive {@code derived.isNoMatch} without ever being sent to a provider. The result is checked against the local
 * validator's hard limits, so a malformed set fails as {@code JEV:INVALID_QUESTION_SET} before any billed call. Shared
 * by {@code evaluate} and {@code evaluate-batch} so both apply exactly the same rules.
 */
final class QuestionResolution {

  private static final String NO_MATCH_OPTION = "noMatchOption";

  private QuestionResolution() {
  }

  /** Resolved questions plus the derived-time annotations and question-set identity. */
  static final class Resolved {

    ObjectNode questions;
    Map<String, String> noMatchOptions;
    String questionSetId;
    String questionSetVersion;
  }

  /** Exactly one of {@code questions} / {@code questionSet} must be supplied. */
  static Resolved resolve(JevConfiguration config, InputStream questions, String questionSet, String questionSetId,
      String questionSetVersion) {
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
    ValidationResult validation = new QuestionSetValidator().validate(resolved.questions, resolved.noMatchOptions);
    if (!validation.isValid()) {
      throw new ModuleException("Invalid questions: " + String.join("; ", validation.getErrors()),
          JevErrorType.INVALID_QUESTION_SET);
    }
    resolved.questionSetId = id;
    resolved.questionSetVersion = version;
    return resolved;
  }

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
}
