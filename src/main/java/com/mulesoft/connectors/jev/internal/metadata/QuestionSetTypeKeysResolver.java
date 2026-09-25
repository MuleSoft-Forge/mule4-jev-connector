package com.mulesoft.connectors.jev.internal.metadata;

import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.metadata.MetadataResolvingException;
import org.mule.sdk.api.metadata.MetadataContext;
import org.mule.sdk.api.metadata.MetadataKey;
import org.mule.sdk.api.metadata.MetadataKeyBuilder;
import org.mule.sdk.api.metadata.resolving.TypeKeysResolver;

import com.mulesoft.connectors.jev.internal.questionset.QuestionSetLoader;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lists the question-set files as metadata keys, so the {@code questionSet} parameter renders as a picklist and drives
 * {@link DecisionOutputResolver}. Listing is best-effort — an unreadable folder yields an empty picklist rather than an
 * error (see open question Q8 on design-time classpath visibility).
 */
public class QuestionSetTypeKeysResolver implements TypeKeysResolver {

  static final String CATEGORY = "JevQuestionSets";
  static final String DEFAULT_LOCATION = "questions/";

  @Override
  public String getCategoryName() {
    return CATEGORY;
  }

  @Override
  public String getResolverName() {
    return "JevQuestionSetKeys";
  }

  @Override
  public Set<MetadataKey> getKeys(MetadataContext context) throws MetadataResolvingException, ConnectionException {
    Set<MetadataKey> keys = new LinkedHashSet<>();
    for (String name : QuestionSetLoader.list(DEFAULT_LOCATION)) {
      keys.add(MetadataKeyBuilder.newKey(name).withDisplayName(name).build());
    }
    return keys;
  }
}
