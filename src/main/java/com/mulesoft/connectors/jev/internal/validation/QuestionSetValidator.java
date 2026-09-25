package com.mulesoft.connectors.jev.internal.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Enforces the documented Jev API limits locally, before any billed call, and emits advisory
 * warnings for question sets that are legal but likely to behave poorly.
 *
 * <p>
 * Hard limits (§3): non-empty {@code questions}; each has {@code type} and {@code instructions};
 * Choice ≤ 255 options; Score 2–10 levels. Warnings (§8.7): Choice with no no-match option,
 * duplicate or empty option descriptions, Score with fewer than 3 levels, Choice with more than 20
 * options.
 */
public final class QuestionSetValidator {

  public static final int MAX_CHOICE_OPTIONS = 255;
  public static final int MIN_SCORE_LEVELS = 2;
  public static final int MAX_SCORE_LEVELS = 10;
  public static final int WARN_CHOICE_OPTIONS = 20;
  public static final int RECOMMENDED_MIN_SCORE_LEVELS = 3;

  private static final Set<String> TYPES = Set.of("noul", "choice", "score");

  /**
   * Validates the {@code questions} map. {@code noMatchOptions} maps a question id to a declared
   * no-match option key so the "missing no-match" warning is not raised where one was configured.
   */
  public ValidationResult validate(JsonNode questions, Map<String, String> noMatchOptions) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    if (questions == null || !questions.isObject() || questions.isEmpty()) {
      errors.add("questions must be a non-empty object mapping id to question");
      return new ValidationResult(errors, warnings);
    }

    Iterator<Map.Entry<String, JsonNode>> it = questions.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> entry = it.next();
      validateQuestion(entry.getKey(), entry.getValue(), noMatchOptions, errors, warnings);
    }
    return new ValidationResult(errors, warnings);
  }

  private void validateQuestion(String id, JsonNode q, Map<String, String> noMatchOptions,
                                List<String> errors, List<String> warnings) {
    if (q == null || !q.isObject()) {
      errors.add(id + ": question must be an object");
      return;
    }

    JsonNode typeNode = q.get("type");
    String type = typeNode != null && typeNode.isTextual() ? typeNode.asText() : null;
    if (type == null || !TYPES.contains(type)) {
      errors.add(id + ": type must be one of noul, choice, score");
    }

    JsonNode instructions = q.get("instructions");
    if (instructions == null || instructions.isNull()
        || (instructions.isTextual() && instructions.asText().isBlank())) {
      errors.add(id + ": instructions are required");
    }

    if ("choice".equals(type)) {
      validateChoice(id, q, noMatchOptions, errors, warnings);
    } else if ("score".equals(type)) {
      validateScore(id, q, errors, warnings);
    }
  }

  private void validateChoice(String id, JsonNode q, Map<String, String> noMatchOptions,
                              List<String> errors, List<String> warnings) {
    JsonNode criteria = q.get("criteria");
    if (criteria == null || !criteria.isObject() || criteria.isEmpty()) {
      errors.add(id + ": choice requires a non-empty criteria map of option to description");
      return;
    }
    if (criteria.size() > MAX_CHOICE_OPTIONS) {
      errors.add(id + ": choice has " + criteria.size() + " options, exceeding the limit of " + MAX_CHOICE_OPTIONS);
    }
    if (criteria.size() > WARN_CHOICE_OPTIONS) {
      warnings.add(id + ": choice has more than " + WARN_CHOICE_OPTIONS + " options; accuracy may drop");
    }
    if (!noMatchOptions.containsKey(id)) {
      warnings.add(id + ": choice has no no-match option; consider adding one");
    }

    Set<String> seenDescriptions = new HashSet<>();
    Iterator<Map.Entry<String, JsonNode>> options = criteria.fields();
    while (options.hasNext()) {
      Map.Entry<String, JsonNode> option = options.next();
      JsonNode desc = option.getValue();
      if (desc == null || desc.isNull() || (desc.isTextual() && desc.asText().isBlank())) {
        warnings.add(id + ": option '" + option.getKey() + "' has an empty description");
      } else if (desc.isTextual() && !seenDescriptions.add(desc.asText())) {
        warnings.add(id + ": duplicate option description '" + desc.asText() + "'");
      }
    }
  }

  private void validateScore(String id, JsonNode q, List<String> errors, List<String> warnings) {
    JsonNode criteria = q.get("criteria");
    if (criteria == null || !criteria.isArray()) {
      errors.add(id + ": score requires an ordered array of level labels");
      return;
    }
    int levels = criteria.size();
    if (levels < MIN_SCORE_LEVELS || levels > MAX_SCORE_LEVELS) {
      errors.add(id + ": score must have between " + MIN_SCORE_LEVELS + " and " + MAX_SCORE_LEVELS
          + " levels, found " + levels);
    } else if (levels < RECOMMENDED_MIN_SCORE_LEVELS) {
      warnings.add(id + ": score has fewer than " + RECOMMENDED_MIN_SCORE_LEVELS + " levels");
    }
  }
}
