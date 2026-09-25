package com.mulesoft.connectors.jev.internal.error;

import org.mule.sdk.api.error.ErrorTypeDefinition;
import org.mule.sdk.api.error.MuleErrors;

import java.util.Optional;

/**
 * Typed error hierarchy for the Jev connector. Every failure surfaces as a {@code JEV:*} error with a parent Mule error
 * type, so flows can catch either the specific or the generic type.
 *
 * <p>
 * Types without an explicit parent are treated by the runtime as children of {@code MULE:ANY}.
 */
public enum JevErrorType implements ErrorTypeDefinition<JevErrorType> {

  /** 401/403 from any route. */
  UNAUTHORIZED(MuleErrors.CLIENT_SECURITY),
  /** 429 after retries. */
  RATE_LIMITED(MuleErrors.CONNECTIVITY),
  /** 529 or 503 after retries. */
  OVERLOADED(MuleErrors.CONNECTIVITY),
  /** Response timeout after retries. */
  TIMEOUT(MuleErrors.CONNECTIVITY),
  /** I/O failure, DNS or TLS problem. */
  CONNECTIVITY(MuleErrors.CONNECTIVITY),
  /** 400/422; message includes the provider's field detail. */
  PROVIDER_VALIDATION(MuleErrors.VALIDATION),
  /** Local validation failed; no billed call was made. */
  INVALID_QUESTION_SET(MuleErrors.VALIDATION),
  /** Choice &gt; 255 options, or candidates &gt; 254. */
  TOO_MANY_OPTIONS(MuleErrors.VALIDATION),
  /** Binary or empty state. */
  INVALID_STATE(MuleErrors.VALIDATION),
  /** {@code items} exceeds {@code maxItems}. */
  BATCH_TOO_LARGE(MuleErrors.VALIDATION),
  /** Unparseable body, missing answer id, or wrong answer type. */
  INVALID_RESPONSE,
  /** Capability absent on the selected route. */
  UNSUPPORTED_BY_PROVIDER,
  /** {@code BudgetGuard} refused the call. */
  BUDGET_EXCEEDED,
  /** apply-policy with {@code raiseOnReview}. */
  BELOW_THRESHOLD,
  /** apply-policy with {@code raiseOnReject}. */
  REJECTED,
  /** Other 5xx after retries. */
  PROVIDER_ERROR;

  private final ErrorTypeDefinition<? extends Enum<?>> parent;

  JevErrorType() {
    this.parent = null;
  }

  JevErrorType(ErrorTypeDefinition<? extends Enum<?>> parent) {
    this.parent = parent;
  }

  @Override
  public Optional<ErrorTypeDefinition<? extends Enum<?>>> getParent() {
    return Optional.ofNullable(parent);
  }
}
