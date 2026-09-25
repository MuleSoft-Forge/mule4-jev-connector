package com.mulesoft.connectors.jev.internal.validation;

import java.util.Collections;
import java.util.List;

/** Outcome of validating a question set: hard errors (API limits) and advisory warnings. */
public final class ValidationResult {

  private final List<String> errors;
  private final List<String> warnings;

  public ValidationResult(List<String> errors, List<String> warnings) {
    this.errors = Collections.unmodifiableList(errors);
    this.warnings = Collections.unmodifiableList(warnings);
  }

  public boolean isValid() {
    return errors.isEmpty();
  }

  public List<String> getErrors() {
    return errors;
  }

  public List<String> getWarnings() {
    return warnings;
  }
}
