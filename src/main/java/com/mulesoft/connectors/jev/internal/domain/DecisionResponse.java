package com.mulesoft.connectors.jev.internal.domain;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A parsed, route-normalised decision response. Adapters unwrap any envelope, pull out usage and any
 * provider-reported cost, and hand back this uniform shape. Connector-computed fields are added
 * later by {@code DerivedComputer}; nothing here is mixed into the provider's own fields.
 */
public final class DecisionResponse {

  private final String model;
  private final String requestedModel;
  private final ObjectNode answers;
  private final Integer inputTokens;
  private final Integer outputTokens;
  private final BigDecimal providerReportedCost;
  private final String providerRequestId;
  private final String rawBody;

  private DecisionResponse(Builder b) {
    this.model = b.model;
    this.requestedModel = b.requestedModel;
    this.answers = b.answers;
    this.inputTokens = b.inputTokens;
    this.outputTokens = b.outputTokens;
    this.providerReportedCost = b.providerReportedCost;
    this.providerRequestId = b.providerRequestId;
    this.rawBody = b.rawBody;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String model() {
    return model;
  }

  /** The model id the adapter actually sent (before the provider echoed one back). */
  public String requestedModel() {
    return requestedModel;
  }

  public ObjectNode answers() {
    return answers;
  }

  public Integer inputTokens() {
    return inputTokens;
  }

  public Integer outputTokens() {
    return outputTokens;
  }

  public BigDecimal providerReportedCost() {
    return providerReportedCost;
  }

  public String providerRequestId() {
    return providerRequestId;
  }

  public String rawBody() {
    return rawBody;
  }

  /** Fluent builder. */
  public static final class Builder {

    private String model;
    private String requestedModel;
    private ObjectNode answers;
    private Integer inputTokens;
    private Integer outputTokens;
    private BigDecimal providerReportedCost;
    private String providerRequestId;
    private String rawBody;

    public Builder model(String value) {
      this.model = value;
      return this;
    }

    public Builder requestedModel(String value) {
      this.requestedModel = value;
      return this;
    }

    public Builder answers(ObjectNode value) {
      this.answers = value;
      return this;
    }

    public Builder inputTokens(Integer value) {
      this.inputTokens = value;
      return this;
    }

    public Builder outputTokens(Integer value) {
      this.outputTokens = value;
      return this;
    }

    public Builder providerReportedCost(BigDecimal value) {
      this.providerReportedCost = value;
      return this;
    }

    public Builder providerRequestId(String value) {
      this.providerRequestId = value;
      return this;
    }

    public Builder rawBody(String value) {
      this.rawBody = value;
      return this;
    }

    public DecisionResponse build() {
      return new DecisionResponse(this);
    }
  }
}
