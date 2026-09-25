package com.mulesoft.connectors.jev.internal.metadata;

import org.mule.metadata.api.model.MetadataType;
import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.metadata.MetadataResolvingException;
import org.mule.sdk.api.metadata.MetadataContext;
import org.mule.sdk.api.metadata.resolving.OutputTypeResolver;

import com.mulesoft.connectors.jev.internal.questionset.QuestionSetLoader;

/**
 * Resolves the DataSense output type of {@code evaluate} from the selected question-set file. With a key, the answer
 * fields are typed per question so {@code payload.answers.<id>} autocompletes; without one (inline questions, or a file
 * that cannot be read at design time), it degrades to the generic open-answers shape. Shares its category with
 * {@link QuestionSetTypeKeysResolver} so the key and output pair up.
 */
public class DecisionOutputResolver implements OutputTypeResolver<String> {

  @Override
  public String getCategoryName() {
    return QuestionSetTypeKeysResolver.CATEGORY;
  }

  @Override
  public String getResolverName() {
    return "JevDecisionOutput";
  }

  @Override
  public MetadataType getOutputType(MetadataContext context, String key)
      throws MetadataResolvingException, ConnectionException {
    if (key == null || key.isBlank()) {
      return DecisionTypeBuilder.decisionOutput(null);
    }
    try {
      return DecisionTypeBuilder
          .decisionOutput(QuestionSetLoader.load(QuestionSetTypeKeysResolver.DEFAULT_LOCATION, key).questions());
    } catch (RuntimeException e) {
      // Unreadable at design time: fall back to the generic shape rather than break metadata resolution.
      return DecisionTypeBuilder.decisionOutput(null);
    }
  }
}
