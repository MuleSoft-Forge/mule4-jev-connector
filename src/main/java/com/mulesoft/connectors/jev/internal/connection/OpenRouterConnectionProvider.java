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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The OpenRouter route: it fronts the same {@code systemOne} contract and reports a per-call price in
 * {@code usage.cost}. Optional attribution headers ({@code HTTP-Referer}, {@code X-Title}) identify the calling app to
 * OpenRouter.
 */
@Alias("openrouter")
@DisplayName("OpenRouter")
public class OpenRouterConnectionProvider extends AbstractJevConnectionProvider {

  @Parameter
  @Password
  @Placement(order = 1)
  @Summary("OpenRouter API key, sent as a Bearer token.")
  private String apiKey;

  @Parameter
  @Optional(defaultValue = RouteDefaults.OPENROUTER_MODEL)
  @Placement(order = 2)
  @Summary("Default model used when an operation does not specify one.")
  private String model;

  @Parameter
  @Optional(defaultValue = RouteDefaults.OPENROUTER_BASE_URL)
  @Placement(order = 3)
  @Summary("Base URL of the OpenRouter API.")
  private String baseUrl;

  @Parameter
  @Optional
  @DisplayName("HTTP Referer")
  @Placement(tab = "Advanced", order = 10)
  @Summary("Optional HTTP-Referer attribution header sent to OpenRouter.")
  private String httpReferer;

  @Parameter
  @Optional
  @DisplayName("App Title")
  @Placement(tab = "Advanced", order = 11)
  @Summary("Optional X-Title attribution header sent to OpenRouter.")
  private String appTitle;

  @Override
  public JevConnection connect() {
    Map<String, String> headers = new LinkedHashMap<>(customHeaders());
    if (httpReferer != null && !httpReferer.isBlank()) {
      headers.put("HTTP-Referer", httpReferer);
    }
    if (appTitle != null && !appTitle.isBlank()) {
      headers.put("X-Title", appTitle);
    }
    SystemOneAdapter adapter = new SystemOneAdapter("openrouter", baseUrl, model, Capabilities.full(true), apiKey,
        headers, CostExtractor.OPENROUTER, RequestIdExtractor.OPENROUTER, transport());
    return connection(adapter);
  }

  @Override
  public ConnectionValidationResult validate(JevConnection connection) {
    return ConnectionValidationResult.success();
  }
}
