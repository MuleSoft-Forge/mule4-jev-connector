package com.mulesoft.connectors.jev.api.attributes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single, append-ready audit record for one decision, exposed as {@code attributes.traceEntry}. A flow accumulates
 * these into a variable — {@code #[(vars.jevTrace default []) + attributes.traceEntry]} — to build an end-to-end trail
 * across several Jev operations without any state or secret text ever appearing in it.
 */
public class TraceEntry implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String step;
  private final String provider;
  private final String model;
  private final long latencyMs;
  private final int attempts;
  private final BigDecimal estimatedCostUsd;
  private final String questionSetId;
  private final List<String> failedOverFrom;

  private TraceEntry(Builder b) {
    this.step = b.step;
    this.provider = b.provider;
    this.model = b.model;
    this.latencyMs = b.latencyMs;
    this.attempts = b.attempts;
    this.estimatedCostUsd = b.estimatedCostUsd;
    this.questionSetId = b.questionSetId;
    this.failedOverFrom = Collections.unmodifiableList(new ArrayList<>(b.failedOverFrom));
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The caller-supplied label from the "Request options" {@code step} parameter, or {@code null}. */
  public String getStep() {
    return step;
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public long getLatencyMs() {
    return latencyMs;
  }

  public int getAttempts() {
    return attempts;
  }

  public BigDecimal getEstimatedCostUsd() {
    return estimatedCostUsd;
  }

  public String getQuestionSetId() {
    return questionSetId;
  }

  public List<String> getFailedOverFrom() {
    return failedOverFrom;
  }

  /** Fluent builder; every field is optional and defaults to a benign value. */
  public static final class Builder {

    private String step;
    private String provider;
    private String model;
    private long latencyMs;
    private int attempts = 1;
    private BigDecimal estimatedCostUsd;
    private String questionSetId;
    private List<String> failedOverFrom = new ArrayList<>();

    public Builder step(String value) {
      this.step = value;
      return this;
    }

    public Builder provider(String value) {
      this.provider = value;
      return this;
    }

    public Builder model(String value) {
      this.model = value;
      return this;
    }

    public Builder latencyMs(long value) {
      this.latencyMs = value;
      return this;
    }

    public Builder attempts(int value) {
      this.attempts = value;
      return this;
    }

    public Builder estimatedCostUsd(BigDecimal value) {
      this.estimatedCostUsd = value;
      return this;
    }

    public Builder questionSetId(String value) {
      this.questionSetId = value;
      return this;
    }

    public Builder failedOverFrom(List<String> value) {
      this.failedOverFrom = value == null ? new ArrayList<>() : new ArrayList<>(value);
      return this;
    }

    public TraceEntry build() {
      return new TraceEntry(this);
    }
  }
}
