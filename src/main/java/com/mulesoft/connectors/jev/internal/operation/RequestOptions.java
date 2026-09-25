package com.mulesoft.connectors.jev.internal.operation;

import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.Parameter;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Placement;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.annotation.values.OfValues;

import com.mulesoft.connectors.jev.internal.value.ModelValueProvider;

/**
 * The parameters shared by every provider-calling operation, grouped under a "Request options" section so the palette
 * stays uncluttered. All four are optional: they refine a single call without changing its meaning.
 */
public class RequestOptions {

  @Parameter
  @Optional
  @OfValues(ModelValueProvider.class)
  @DisplayName("Model override")
  @Placement(tab = "Request options")
  @Summary("Model id for this call; overrides the connection's default model.")
  private String modelOverride;

  @Parameter
  @Optional(defaultValue = "false")
  @DisplayName("Include raw response")
  @Placement(tab = "Request options")
  @Summary("Attach the provider's raw response body to attributes.rawResponse (never logged).")
  private boolean includeRawResponse;

  @Parameter
  @Optional(defaultValue = "false")
  @DisplayName("Use cache")
  @Placement(tab = "Request options")
  @Summary("Serve this call from the decision cache when enabled on the config (cache lands in M4).")
  private boolean useCache;

  @Parameter
  @Optional
  @DisplayName("Step")
  @Placement(tab = "Request options")
  @Summary("A label copied into attributes.traceEntry.step so a flow can build an audit trail.")
  private String step;

  /** The model to send, or {@code null} to use the connection default. */
  public String getModelOverride() {
    return modelOverride;
  }

  public boolean isIncludeRawResponse() {
    return includeRawResponse;
  }

  /** Per-call cache opt-in; effective only once caching is wired in M4. */
  public boolean isUseCache() {
    return useCache;
  }

  public String getStep() {
    return step;
  }
}
