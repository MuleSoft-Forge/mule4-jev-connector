package com.mulesoft.connectors.jev.internal.connection;

import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.Parameter;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Placement;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.annotation.semantics.security.Password;
import org.mule.sdk.api.connectivity.ConnectionValidationResult;

import com.mulesoft.connectors.jev.internal.provider.Capabilities;
import com.mulesoft.connectors.jev.internal.provider.CostExtractor;
import com.mulesoft.connectors.jev.internal.provider.RequestIdExtractor;
import com.mulesoft.connectors.jev.internal.provider.SystemOneAdapter;

/**
 * The TypeSafe direct route: it speaks the canonical {@code systemOne} contract, reports token usage and supports model
 * listing. Cost is estimated from tokens, since the direct API does not report a per-call price.
 */
@Alias("typesafe")
@DisplayName("TypeSafe")
public class TypeSafeConnectionProvider extends AbstractJevConnectionProvider {

  @Parameter
  @Password
  @Placement(order = 1)
  @Summary("TypeSafe API key, sent as a Bearer token.")
  private String apiKey;

  @Parameter
  @Optional(defaultValue = "https://api.typesafe.ai")
  @Placement(order = 2)
  @Summary("Base URL of the TypeSafe API.")
  private String baseUrl;

  @Parameter
  @Optional
  @Placement(order = 3)
  @Summary("Default model used when an operation does not specify one.")
  private String model;

  @Override
  public JevConnection connect() {
    SystemOneAdapter adapter = new SystemOneAdapter("typesafe", baseUrl, model, Capabilities.full(true), apiKey,
        customHeaders(), CostExtractor.NONE, RequestIdExtractor.header("x-typesafe-request-id"), transport());
    return new JevConnection(adapter, fallbackAdapters(), engine());
  }

  @Override
  public ConnectionValidationResult validate(JevConnection connection) {
    return ConnectionValidationResult.success();
  }
}
