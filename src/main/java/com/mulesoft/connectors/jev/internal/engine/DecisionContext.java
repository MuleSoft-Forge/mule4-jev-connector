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
  private final String step;

  public DecisionContext(BigDecimal pricePerMillionInputTokens, boolean includeRawResponse) {
    this(pricePerMillionInputTokens, includeRawResponse, null);
  }

  public DecisionContext(BigDecimal pricePerMillionInputTokens, boolean includeRawResponse, String step) {
    this.pricePerMillionInputTokens = pricePerMillionInputTokens;
    this.includeRawResponse = includeRawResponse;
    this.step = step;
  }

  /** Price per million input tokens (USD), or {@code null} to skip cost estimation. */
  public BigDecimal pricePerMillionInputTokens() {
    return pricePerMillionInputTokens;
  }

  public boolean includeRawResponse() {
    return includeRawResponse;
  }

  /** The caller-supplied step label copied into {@code attributes.traceEntry.step}, or {@code null}. */
  public String step() {
    return step;
  }
}
