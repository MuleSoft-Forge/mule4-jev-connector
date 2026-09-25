package com.mulesoft.connectors.jev.internal.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Canonical, provider-agnostic decision request. Operations build one of these and hand it to the
 * engine; route differences live only in the adapters.
 *
 * <p>
 * {@code questions} is already cleaned for the wire: connector-side annotations such as
 * {@code noMatchOption} have been stripped and captured in {@link #noMatchOptions()} so they can
 * drive {@code derived.isNoMatch} without being sent to the provider.
 */
public final class DecisionRequest {

  private final JsonNode state;
  private final String requestedModel;
  private final ObjectNode questions;
  private final Map<String, String> noMatchOptions;
  private final String questionSetId;
  private final String questionSetVersion;

  public DecisionRequest(JsonNode state, String requestedModel, ObjectNode questions,
                         Map<String, String> noMatchOptions, String questionSetId, String questionSetVersion) {
    this.state = state;
    this.requestedModel = requestedModel;
    this.questions = questions;
    this.noMatchOptions = Collections.unmodifiableMap(new HashMap<>(noMatchOptions));
    this.questionSetId = questionSetId;
    this.questionSetVersion = questionSetVersion;
  }

  public JsonNode state() {
    return state;
  }

  /** The model the caller asked for, or {@code null} to use the route's configured default. */
  public String requestedModel() {
    return requestedModel;
  }

  public ObjectNode questions() {
    return questions;
  }

  public Map<String, String> noMatchOptions() {
    return noMatchOptions;
  }

  public String questionSetId() {
    return questionSetId;
  }

  public String questionSetVersion() {
    return questionSetVersion;
  }
}
