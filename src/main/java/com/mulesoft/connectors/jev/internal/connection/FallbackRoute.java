package com.mulesoft.connectors.jev.internal.connection;

import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.Parameter;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.annotation.semantics.security.Password;

/**
 * An inline definition of a fallback route on a connection. Failover stays inside a single connection (no cross-config
 * lifecycle): the primary provider builds one adapter per fallback, in order, and the engine tries them when the
 * primary fails with a retryable-but-terminal connectivity-class error.
 *
 * <p>
 * All credentials are supplied inline; a fallback cannot reference another {@code jev:config}.
 */
public class FallbackRoute {

  @Parameter
  @DisplayName("Route")
  @Summary("Which provider this fallback speaks to.")
  private RouteType route;

  @Parameter
  @Optional
  @Password
  @Summary("API key or token for the fallback route, sent as a Bearer credential.")
  private String apiKey;

  @Parameter
  @Optional
  @Summary("Base URL override; defaults to the route's standard endpoint. Required for the compatible route.")
  private String baseUrl;

  @Parameter
  @Optional
  @Summary("Default model for this fallback; defaults to the route's standard model.")
  private String model;

  @Parameter
  @Optional
  @Summary("Cloudflare account id; required only when the fallback route is Cloudflare.")
  private String accountId;

  @Parameter
  @Optional(defaultValue = "false")
  @Summary("For the compatible route only: whether the gateway can enumerate models.")
  private boolean supportsModelList;

  public RouteType getRoute() {
    return route;
  }

  public String getApiKey() {
    return apiKey;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getModel() {
    return model;
  }

  public String getAccountId() {
    return accountId;
  }

  public boolean isSupportsModelList() {
    return supportsModelList;
  }
}
