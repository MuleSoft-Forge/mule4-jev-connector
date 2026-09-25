package com.mulesoft.connectors.jev.internal.engine;

import java.math.BigDecimal;

/**
 * Per-evaluation knobs the engine needs but that are not part of the wire request: the input-token price used to
 * estimate cost when a route reports none, and whether to attach the raw response body to attributes. Question-set
 * identity travels on the {@code DecisionRequest}.
 */
public final class DecisionContext {

  private final BigDecimal pricePerMillionInputTokens;
  private final boolean includeRawResponse;

  public DecisionContext(BigDecimal pricePerMillionInputTokens, boolean includeRawResponse) {
    this.pricePerMillionInputTokens = pricePerMillionInputTokens;
    this.includeRawResponse = includeRawResponse;
  }

  /** Price per million input tokens (USD), or {@code null} to skip cost estimation. */
  public BigDecimal pricePerMillionInputTokens() {
    return pricePerMillionInputTokens;
  }

  public boolean includeRawResponse() {
    return includeRawResponse;
  }
}
