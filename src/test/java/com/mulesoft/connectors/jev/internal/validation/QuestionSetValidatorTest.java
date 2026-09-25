package com.mulesoft.connectors.jev.internal.validation;

import com.mulesoft.connectors.jev.internal.util.Json;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSetValidatorTest {

  private final QuestionSetValidator validator = new QuestionSetValidator();

  @Test
  void rejectsEmptyQuestionSet() {
    ValidationResult result = validator.validate(Json.read("{}"), Map.of());
    assertFalse(result.isValid());
  }

  @Test
  void requiresTypeAndInstructions() {
    JsonNode questions = Json.read("{\"q\":{\"type\":\"banana\"}}");
    ValidationResult result = validator.validate(questions, Map.of());
    assertFalse(result.isValid());
    assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("type must be one of")));
    assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("instructions are required")));
  }

  @Test
  void acceptsValidNoul() {
    JsonNode questions = Json.read("{\"q\":{\"type\":\"noul\",\"instructions\":\"Is it fraud?\"}}");
    assertTrue(validator.validate(questions, Map.of()).isValid());
  }

  @Test
  void warnsWhenChoiceHasNoNoMatchOption() {
    JsonNode questions = Json
        .read("{\"q\":{\"type\":\"choice\",\"instructions\":\"pick\",\"criteria\":{\"a\":\"A\",\"b\":\"B\"}}}");
    ValidationResult result = validator.validate(questions, Map.of());
    assertTrue(result.isValid());
    assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("no no-match option")));
  }

  @Test
  void doesNotWarnWhenNoMatchConfigured() {
    JsonNode questions = Json
        .read("{\"q\":{\"type\":\"choice\",\"instructions\":\"pick\",\"criteria\":{\"a\":\"A\",\"b\":\"B\"}}}");
    ValidationResult result = validator.validate(questions, Map.of("q", "a"));
    assertTrue(result.getWarnings().stream().noneMatch(w -> w.contains("no no-match option")));
  }

  @Test
  void rejectsScoreOutsideLevelRange() {
    JsonNode tooFew = Json.read("{\"q\":{\"type\":\"score\",\"instructions\":\"rate\",\"criteria\":[\"only\"]}}");
    assertFalse(validator.validate(tooFew, Map.of()).isValid());
  }

  @Test
  void warnsWhenScoreHasFewerThanRecommendedLevels() {
    JsonNode questions = Json
        .read("{\"q\":{\"type\":\"score\",\"instructions\":\"rate\",\"criteria\":[\"low\",\"high\"]}}");
    ValidationResult result = validator.validate(questions, Map.of());
    assertTrue(result.isValid());
    assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("fewer than")));
  }
}
