package com.mulesoft.connectors.jev.api.attributes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Out-of-band metadata attached to every decision result. It is a plain, DataWeave-friendly POJO: flows read
 * {@code attributes.provider}, {@code attributes.usage.inputTokens} and so on. No secret or state text is ever placed
 * here.
 */
public class DecisionAttributes implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String provider;
  private final String requestedModel;
  private final String model;
  private final TokenUsage usage;
  private final BigDecimal estimatedCostUsd;
  private final String costSource;
  private final long latencyMs;
  private final int attempts;
  private final List<String> failedOverFrom;
  private final boolean cacheHit;
  private final String questionSetId;
  private final String questionSetVersion;
  private final String stateHash;
  private final String providerRequestId;
  private final String rawResponse;

  private DecisionAttributes(Builder b) {
    this.provider = b.provider;
    this.requestedModel = b.requestedModel;
    this.model = b.model;
    this.usage = b.usage;
    this.estimatedCostUsd = b.estimatedCostUsd;
    this.costSource = b.costSource;
    this.latencyMs = b.latencyMs;
    this.attempts = b.attempts;
    this.failedOverFrom = Collections.unmodifiableList(new ArrayList<>(b.failedOverFrom));
    this.cacheHit = b.cacheHit;
    this.questionSetId = b.questionSetId;
    this.questionSetVersion = b.questionSetVersion;
    this.stateHash = b.stateHash;
    this.providerRequestId = b.providerRequestId;
    this.rawResponse = b.rawResponse;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getProvider() {
    return provider;
  }

  public String getRequestedModel() {
    return requestedModel;
  }

  public String getModel() {
    return model;
  }

  public TokenUsage getUsage() {
    return usage;
  }

  public BigDecimal getEstimatedCostUsd() {
    return estimatedCostUsd;
  }

  public String getCostSource() {
    return costSource;
  }

  public long getLatencyMs() {
    return latencyMs;
  }

  public int getAttempts() {
    return attempts;
  }

  public List<String> getFailedOverFrom() {
    return failedOverFrom;
  }

  public boolean isCacheHit() {
    return cacheHit;
  }

  public String getQuestionSetId() {
    return questionSetId;
  }

  public String getQuestionSetVersion() {
    return questionSetVersion;
  }

  public String getStateHash() {
    return stateHash;
  }

  public String getProviderRequestId() {
    return providerRequestId;
  }

  public String getRawResponse() {
    return rawResponse;
  }

  /** Fluent builder; every field is optional and defaults to a benign value. */
  public static final class Builder {

    private String provider;
    private String requestedModel;
    private String model;
    private TokenUsage usage;
    private BigDecimal estimatedCostUsd;
    private String costSource;
    private long latencyMs;
    private int attempts = 1;
    private List<String> failedOverFrom = new ArrayList<>();
    private boolean cacheHit;
    private String questionSetId;
    private String questionSetVersion;
    private String stateHash;
    private String providerRequestId;
    private String rawResponse;

    public Builder provider(String value) {
      this.provider = value;
      return this;
    }

    public Builder requestedModel(String value) {
      this.requestedModel = value;
      return this;
    }

    public Builder model(String value) {
      this.model = value;
      return this;
    }

    public Builder usage(TokenUsage value) {
      this.usage = value;
      return this;
    }

    public Builder estimatedCostUsd(BigDecimal value) {
      this.estimatedCostUsd = value;
      return this;
    }

    public Builder costSource(String value) {
      this.costSource = value;
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

    public Builder failedOverFrom(List<String> value) {
      this.failedOverFrom = value == null ? new ArrayList<>() : new ArrayList<>(value);
      return this;
    }

    public Builder cacheHit(boolean value) {
      this.cacheHit = value;
      return this;
    }

    public Builder questionSetId(String value) {
      this.questionSetId = value;
      return this;
    }

    public Builder questionSetVersion(String value) {
      this.questionSetVersion = value;
      return this;
    }

    public Builder stateHash(String value) {
      this.stateHash = value;
      return this;
    }

    public Builder providerRequestId(String value) {
      this.providerRequestId = value;
      return this;
    }

    public Builder rawResponse(String value) {
      this.rawResponse = value;
      return this;
    }

    public DecisionAttributes build() {
      return new DecisionAttributes(this);
    }
  }
}
