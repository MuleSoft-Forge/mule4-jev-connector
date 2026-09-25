package com.mulesoft.connectors.jev.internal.engine;

import com.mulesoft.connectors.jev.api.attributes.DecisionAttributes;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The result of a decision: the enriched answers payload the operation streams back, plus the out-of-band
 * {@link DecisionAttributes}. Operations turn the payload into a JSON {@code InputStream} result; nothing here holds
 * runtime types.
 */
public final class DecisionOutcome {

  private final ObjectNode payload;
  private final DecisionAttributes attributes;

  public DecisionOutcome(ObjectNode payload, DecisionAttributes attributes) {
    this.payload = payload;
    this.attributes = attributes;
  }

  public ObjectNode payload() {
    return payload;
  }

  public DecisionAttributes attributes() {
    return attributes;
  }
}
