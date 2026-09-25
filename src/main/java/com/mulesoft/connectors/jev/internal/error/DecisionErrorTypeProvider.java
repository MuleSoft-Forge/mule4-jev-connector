package com.mulesoft.connectors.jev.internal.error;

import org.mule.sdk.api.annotation.error.ErrorTypeProvider;
import org.mule.sdk.api.error.ErrorTypeDefinition;

import java.util.Set;

/**
 * Declares the {@code JEV:*} errors a decision operation may raise, so the runtime shows them in the palette and flows
 * can catch them by type. It covers the transport and validation errors {@code evaluate} can produce.
 */
public class DecisionErrorTypeProvider implements ErrorTypeProvider {

  @Override
  @SuppressWarnings("rawtypes")
  public Set<ErrorTypeDefinition> getErrorTypes() {
    return Set.of(JevErrorType.UNAUTHORIZED, JevErrorType.RATE_LIMITED, JevErrorType.OVERLOADED, JevErrorType.TIMEOUT,
        JevErrorType.CONNECTIVITY, JevErrorType.PROVIDER_VALIDATION, JevErrorType.PROVIDER_ERROR,
        JevErrorType.INVALID_RESPONSE, JevErrorType.UNSUPPORTED_BY_PROVIDER, JevErrorType.BUDGET_EXCEEDED);
  }
}
