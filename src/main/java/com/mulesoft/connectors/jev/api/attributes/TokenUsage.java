package com.mulesoft.connectors.jev.api.attributes;

import java.io.Serializable;

/**
 * Token usage reported by a route. Either field is {@code null} when the route does not report it. Jev bills on input
 * tokens only; output tokens are informational.
 */
public class TokenUsage implements Serializable {

  private static final long serialVersionUID = 1L;

  private final Integer inputTokens;
  private final Integer outputTokens;

  public TokenUsage(Integer inputTokens, Integer outputTokens) {
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
  }

  public Integer getInputTokens() {
    return inputTokens;
  }

  public Integer getOutputTokens() {
    return outputTokens;
  }
}
