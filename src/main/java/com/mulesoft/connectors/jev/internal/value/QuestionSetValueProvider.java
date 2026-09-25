package com.mulesoft.connectors.jev.internal.value;

import org.mule.sdk.api.values.Value;
import org.mule.sdk.api.values.ValueBuilder;
import org.mule.sdk.api.values.ValueProvider;
import org.mule.sdk.api.values.ValueResolvingException;

import com.mulesoft.connectors.jev.internal.questionset.QuestionSetLoader;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Offers the question-set file names for the {@code questionSet} parameter at design time, listing the connector's
 * default {@code questions/} classpath folder. Listing is best-effort: an unreadable or absent folder yields an empty
 * dropdown rather than an error, so design-time editing never breaks. Whether an IDE can see the application's own
 * question-set files here is the subject of open question Q8; runtime resolution always reads the file directly.
 */
public class QuestionSetValueProvider implements ValueProvider {

  /** The connector's conventional question-set folder; matches the config's {@code defaultQuestionSetsLocation}. */
  private static final String DEFAULT_LOCATION = "questions/";

  @Override
  public Set<Value> resolve() throws ValueResolvingException {
    Set<Value> values = new LinkedHashSet<>();
    for (String name : QuestionSetLoader.list(DEFAULT_LOCATION)) {
      values.add(ValueBuilder.newValue(name).build());
    }
    return values;
  }

  @Override
  public String getId() {
    return "jev-question-set-values";
  }
}
