package com.mulesoft.connectors.jev.internal.provider;

import java.io.Serializable;
import java.util.Objects;

/**
 * Static, per-adapter capability descriptor. Operations consult these before sending a request so an unsupported
 * feature fails locally rather than at the provider. Values are fixed per route for v1 and may later be overridden by
 * verification.
 */
public class Capabilities implements Serializable {

  private static final long serialVersionUID = 1L;

  private final boolean supportsNoul;
  private final boolean supportsChoice;
  private final boolean supportsScore;
  private final boolean returnsConfidence;
  private final boolean supportsModelList;
  private final boolean supportsStructuredInstructions;
  private final int maxChoiceOptions;
  private final int maxScoreLevels;

  public Capabilities(boolean supportsNoul, boolean supportsChoice, boolean supportsScore, boolean returnsConfidence,
      boolean supportsModelList, boolean supportsStructuredInstructions, int maxChoiceOptions, int maxScoreLevels) {
    this.supportsNoul = supportsNoul;
    this.supportsChoice = supportsChoice;
    this.supportsScore = supportsScore;
    this.returnsConfidence = returnsConfidence;
    this.supportsModelList = supportsModelList;
    this.supportsStructuredInstructions = supportsStructuredInstructions;
    this.maxChoiceOptions = maxChoiceOptions;
    this.maxScoreLevels = maxScoreLevels;
  }

  /** Capabilities of a fully-featured route that speaks the canonical TypeSafe contract. */
  public static Capabilities full(boolean supportsModelList) {
    return new Capabilities(true, true, true, true, supportsModelList, true, 255, 10);
  }

  public boolean isSupportsNoul() {
    return supportsNoul;
  }

  public boolean isSupportsChoice() {
    return supportsChoice;
  }

  public boolean isSupportsScore() {
    return supportsScore;
  }

  public boolean isReturnsConfidence() {
    return returnsConfidence;
  }

  public boolean isSupportsModelList() {
    return supportsModelList;
  }

  public boolean isSupportsStructuredInstructions() {
    return supportsStructuredInstructions;
  }

  public int getMaxChoiceOptions() {
    return maxChoiceOptions;
  }

  public int getMaxScoreLevels() {
    return maxScoreLevels;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Capabilities)) {
      return false;
    }
    Capabilities that = (Capabilities) o;
    return supportsNoul == that.supportsNoul && supportsChoice == that.supportsChoice
        && supportsScore == that.supportsScore && returnsConfidence == that.returnsConfidence
        && supportsModelList == that.supportsModelList
        && supportsStructuredInstructions == that.supportsStructuredInstructions
        && maxChoiceOptions == that.maxChoiceOptions && maxScoreLevels == that.maxScoreLevels;
  }

  @Override
  public int hashCode() {
    return Objects.hash(supportsNoul, supportsChoice, supportsScore, returnsConfidence, supportsModelList,
        supportsStructuredInstructions, maxChoiceOptions, maxScoreLevels);
  }
}
