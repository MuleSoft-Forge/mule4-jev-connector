package com.mulesoft.connectors.jev.internal.error;

import org.mule.sdk.api.annotation.error.ErrorTypeProvider;
import org.mule.sdk.api.error.ErrorTypeDefinition;

import java.util.Set;

/**
 * Declares the {@code JEV:*} errors the scale operations ({@code evaluate-batch}, {@code filter}) may raise. It adds
 * {@code BATCH_TOO_LARGE} to the transport and validation set a single decision can produce; per-item failures are
 * reported inside the payload rather than raised, so they are not listed here.
 */
public class BatchErrorTypeProvider implements ErrorTypeProvider {

  @Override
  @SuppressWarnings("rawtypes")
  public Set<ErrorTypeDefinition> getErrorTypes() {
    return Set.of(JevErrorType.BATCH_TOO_LARGE, JevErrorType.INVALID_QUESTION_SET, JevErrorType.INVALID_STATE,
        JevErrorType.BUDGET_EXCEEDED, JevErrorType.UNAUTHORIZED, JevErrorType.RATE_LIMITED, JevErrorType.OVERLOADED,
        JevErrorType.TIMEOUT, JevErrorType.CONNECTIVITY, JevErrorType.PROVIDER_VALIDATION, JevErrorType.PROVIDER_ERROR,
        JevErrorType.INVALID_RESPONSE, JevErrorType.UNSUPPORTED_BY_PROVIDER);
  }
}
