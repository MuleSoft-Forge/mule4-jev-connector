package com.mulesoft.connectors.jev.internal.questionset;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A parsed question-set file: the reusable definition a flow references by name instead of inlining {@code questions}.
 * It carries the question map sent on the wire plus the identity ({@code id}, {@code version}) recorded in
 * {@code attributes} and an optional {@code policy} block that {@code apply-policy} can read back.
 *
 * <p>
 * The {@code questions} node is the same shape as the inline {@code questions} parameter, so both paths converge on one
 * canonical {@link com.mulesoft.connectors.jev.internal.domain.DecisionRequest}.
 */
public final class QuestionSet {

  private final String id;
  private final String version;
  private final String description;
  private final ObjectNode questions;
  private final ObjectNode policy;

  public QuestionSet(String id, String version, String description, ObjectNode questions, ObjectNode policy) {
    this.id = id;
    this.version = version;
    this.description = description;
    this.questions = questions;
    this.policy = policy;
  }

  /** The question-set id recorded in {@code attributes.questionSetId}; falls back to the file name when absent. */
  public String id() {
    return id;
  }

  /** The version recorded in {@code attributes.questionSetVersion}, or {@code null}. */
  public String version() {
    return version;
  }

  public String description() {
    return description;
  }

  /** The question map, keyed by question id, ready to hand to the engine. Never {@code null}. */
  public ObjectNode questions() {
    return questions;
  }

  /** The embedded policy block, or {@code null} when the file declares none. */
  public ObjectNode policy() {
    return policy;
  }
}
