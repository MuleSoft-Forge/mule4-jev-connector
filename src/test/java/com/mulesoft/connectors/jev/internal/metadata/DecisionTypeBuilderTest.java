package com.mulesoft.connectors.jev.internal.metadata;

import org.mule.metadata.api.model.BooleanType;
import org.mule.metadata.api.model.MetadataType;
import org.mule.metadata.api.model.NumberType;
import org.mule.metadata.api.model.ObjectType;
import org.mule.metadata.api.model.StringType;

import com.mulesoft.connectors.jev.internal.util.Json;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionTypeBuilderTest {

  private static ObjectType object(MetadataType type) {
    return assertInstanceOf(ObjectType.class, type);
  }

  private static MetadataType field(ObjectType parent, String name) {
    return parent.getFieldByName(name).orElseThrow(() -> new AssertionError("missing field: " + name)).getValue();
  }

  @Test
  void choiceQuestionTypesAnswerPerSevenShape() {
    ObjectNode questions = (ObjectNode) Json
        .read("{\"team\":{\"type\":\"choice\"},\"urgent\":{\"type\":\"noul\"},\"mood\":{\"type\":\"score\"}}");

    ObjectType root = object(DecisionTypeBuilder.decisionOutput(questions));
    assertInstanceOf(StringType.class, field(root, "model"));
    ObjectType answers = object(field(root, "answers"));

    // choice: payload.answers.team.choice is a string, derived.margin a number, derived.isNoMatch a boolean.
    ObjectType team = object(field(answers, "team"));
    assertInstanceOf(StringType.class, field(team, "choice"));
    assertTrue(object(field(team, "probabilities")).isOpen());
    assertInstanceOf(NumberType.class, field(team, "confidence"));
    ObjectType teamDerived = object(field(team, "derived"));
    assertInstanceOf(NumberType.class, field(teamDerived, "margin"));
    assertInstanceOf(StringType.class, field(teamDerived, "runnerUp"));
    assertInstanceOf(BooleanType.class, field(teamDerived, "isNoMatch"));

    // noul: single numeric field.
    assertInstanceOf(NumberType.class, field(object(field(answers, "urgent")), "noul"));

    // score: score/level are numbers, levelLabel a string.
    ObjectType mood = object(field(answers, "mood"));
    assertInstanceOf(NumberType.class, field(mood, "score"));
    assertTrue(object(field(mood, "legend")).isOpen());
    ObjectType moodDerived = object(field(mood, "derived"));
    assertInstanceOf(NumberType.class, field(moodDerived, "level"));
    assertInstanceOf(StringType.class, field(moodDerived, "levelLabel"));
  }

  @Test
  void nullQuestionsYieldGenericOpenAnswers() {
    ObjectType root = object(DecisionTypeBuilder.decisionOutput(null));
    assertInstanceOf(StringType.class, field(root, "model"));
    assertTrue(object(field(root, "answers")).isOpen(), "generic answers map should be open");
  }

  @Test
  void unknownQuestionTypeLeavesAnswerOpen() {
    ObjectNode questions = (ObjectNode) Json.read("{\"q\":{\"type\":\"mystery\"}}");
    ObjectType answers = object(field(object(DecisionTypeBuilder.decisionOutput(questions)), "answers"));
    assertTrue(object(field(answers, "q")).isOpen(), "unknown answer type should stay open");
  }
}
