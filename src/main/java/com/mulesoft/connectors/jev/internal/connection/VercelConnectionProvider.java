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
 * The Vercel AI Gateway route: it fronts the same {@code systemOne} contract, reports a per-call price as a string in
 * {@code provider_metadata.gateway.cost}, and exposes its trace id as {@code provider_metadata.gateway.generationId}.
 * Authentication is an AI Gateway key or an OIDC token.
 */
@Alias("vercel")
@DisplayName("Vercel AI Gateway")
public class VercelConnectionProvider extends AbstractJevConnectionProvider {

  @Parameter
  @Password
  @Placement(order = 1)
  @Summary("Vercel AI Gateway key or OIDC token, sent as a Bearer credential.")
  private String apiKey;

  @Parameter
  @Optional(defaultValue = RouteDefaults.VERCEL_MODEL)
  @Placement(order = 2)
  @Summary("Default model used when an operation does not specify one.")
  private String model;

  @Parameter
  @Optional(defaultValue = RouteDefaults.VERCEL_BASE_URL)
  @Placement(order = 3)
  @Summary("Base URL of the Vercel AI Gateway.")
  private String baseUrl;

  @Override
  public JevConnection connect() {
    SystemOneAdapter adapter = new SystemOneAdapter("vercel", baseUrl, model, Capabilities.full(true), apiKey,
        customHeaders(), CostExtractor.VERCEL, RequestIdExtractor.VERCEL_GENERATION_ID, transport());
    return connection(adapter);
  }

  @Override
  public ConnectionValidationResult validate(JevConnection connection) {
    return ConnectionValidationResult.success();
  }
}
