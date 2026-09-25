package com.mulesoft.connectors.jev.api.attributes;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Out-of-band totals for a batch decision. A plain, DataWeave-friendly POJO: a flow reads {@code attributes.total},
 * {@code attributes.succeeded}, {@code attributes.usage.inputTokens} and so on. No state text is ever placed here.
 */
public class BatchAttributes implements Serializable {

  private static final long serialVersionUID = 1L;

  private final int total;
  private final int succeeded;
  private final int failed;
  private final int skippedBudget;
  private final int cached;
  private final TokenUsage usage;
  private final BigDecimal estimatedCostUsd;

  public BatchAttributes(int total, int succeeded, int failed, int skippedBudget, int cached, TokenUsage usage,
      BigDecimal estimatedCostUsd) {
    this.total = total;
    this.succeeded = succeeded;
    this.failed = failed;
    this.skippedBudget = skippedBudget;
    this.cached = cached;
    this.usage = usage;
    this.estimatedCostUsd = estimatedCostUsd;
  }

  public int getTotal() {
    return total;
  }

  public int getSucceeded() {
    return succeeded;
  }

  public int getFailed() {
    return failed;
  }

  /** Items refused because a budget limit was already reached. */
  public int getSkippedBudget() {
    return skippedBudget;
  }

  /** Items served from the decision cache without a billed call. */
  public int getCached() {
    return cached;
  }

  /** Summed input/output tokens across the billed items. */
  public TokenUsage getUsage() {
    return usage;
  }

  /** Summed estimated cost across the billed items, or {@code null} when no item reported a cost. */
  public BigDecimal getEstimatedCostUsd() {
    return estimatedCostUsd;
  }
}
