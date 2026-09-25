package com.mulesoft.connectors.jev.internal.metadata;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.builder.ObjectTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.api.model.MetadataType;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Builds the DataSense output type for a decision from a question set, so Studio and ACB autocomplete
 * {@code payload.answers.<id>.choice}, {@code .noul}, {@code .score} and {@code .derived.level} per the §7 canonical
 * shape. When no question set is known (inline {@code questions}), it returns the generic shape with an open
 * {@code answers} map. Pure and side-effect free, so the resolver stays a thin adapter and the type is unit-tested
 * directly.
 */
public final class DecisionTypeBuilder {

  private DecisionTypeBuilder() {
  }

  /**
   * @param questions
   *          the question map (id → question), or {@code null} to build the generic, open-answers type.
   * @return the metadata type of the evaluate payload {@code {model, answers}}.
   */
  public static MetadataType decisionOutput(ObjectNode questions) {
    ObjectTypeBuilder root = BaseTypeBuilder.create(MetadataFormat.JSON).objectType().id("jev-decision");
    root.addField().key("model").value().stringType();
    ObjectTypeBuilder answers = root.addField().key("answers").value().objectType().id("jev-answers");
    if (questions == null || questions.isEmpty()) {
      answers.openWith().anyType();
      return root.build();
    }
    Iterator<Map.Entry<String, JsonNode>> it = questions.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> entry = it.next();
      String type = entry.getValue().path("type").asText("");
      addAnswer(answers, entry.getKey(), type);
    }
    return root.build();
  }

  private static void addAnswer(ObjectTypeBuilder answers, String id, String type) {
    ObjectTypeBuilder answer = answers.addField().key(id).value().objectType().id("jev-answer-" + id);
    answer.addField().key("type").value().stringType();
    switch (type) {
      case "choice" :
        answer.addField().key("choice").value().stringType();
        answer.addField().key("probabilities").value().objectType().openWith().numberType();
        answer.addField().key("confidence").value().numberType();
        ObjectTypeBuilder choiceDerived = answer.addField().key("derived").value().objectType();
        choiceDerived.addField().key("margin").value().numberType();
        choiceDerived.addField().key("runnerUp").value().stringType();
        choiceDerived.addField().key("isNoMatch").value().booleanType();
        break;
      case "score" :
        answer.addField().key("score").value().numberType();
        answer.addField().key("legend").value().objectType().openWith().stringType();
        answer.addField().key("probabilities").value().objectType().openWith().numberType();
        answer.addField().key("confidence").value().numberType();
        ObjectTypeBuilder scoreDerived = answer.addField().key("derived").value().objectType();
        scoreDerived.addField().key("level").value().numberType();
        scoreDerived.addField().key("levelLabel").value().stringType();
        break;
      case "noul" :
        answer.addField().key("noul").value().numberType();
        break;
      default :
        // Unknown or dynamic question type: leave the answer open rather than guess its fields.
        answer.open();
    }
  }
}
