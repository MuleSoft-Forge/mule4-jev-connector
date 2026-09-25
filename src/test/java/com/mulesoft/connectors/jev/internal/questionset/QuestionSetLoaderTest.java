package com.mulesoft.connectors.jev.internal.questionset;

import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSetLoaderTest {

  @Test
  void loadsBundledQuestionSetByBareName() {
    QuestionSet set = QuestionSetLoader.load("questions/", "ticket-triage");
    assertEquals("ticket-triage", set.id());
    assertEquals("1.0.0", set.version());
    assertTrue(set.questions().has("team"));
    assertTrue(set.questions().has("urgent"));
    assertNotNull(set.policy());
    assertTrue(set.policy().has("team"));
  }

  @Test
  void toleratesJsonSuffixAndMissingTrailingSlash() {
    QuestionSet set = QuestionSetLoader.load("questions", "ticket-triage.json");
    assertEquals("ticket-triage", set.id());
  }

  @Test
  void listsBundledFiles() {
    Set<String> names = QuestionSetLoader.list("questions/");
    assertTrue(names.contains("ticket-triage.json"), "expected ticket-triage.json in " + names);
  }

  @Test
  void missingFileRaisesInvalidQuestionSet() {
    ModuleException e = assertThrows(ModuleException.class,
        () -> QuestionSetLoader.load("questions/", "does-not-exist"));
    assertEquals(JevErrorType.INVALID_QUESTION_SET, e.getType());
  }

  @Test
  void parseUsesFallbackIdAndTreatsMissingPolicyAsNull() {
    QuestionSet set = QuestionSetLoader.parse("fallback",
        Json.read("{\"questions\":{\"q\":{\"type\":\"noul\",\"instructions\":\"ok?\"}}}"));
    assertEquals("fallback", set.id());
    assertNull(set.policy());
    assertNull(set.version());
  }

  @Test
  void parseRejectsEmptyQuestions() {
    ModuleException e = assertThrows(ModuleException.class,
        () -> QuestionSetLoader.parse("x", Json.read("{\"questions\":{}}")));
    assertEquals(JevErrorType.INVALID_QUESTION_SET, e.getType());
  }
}
