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
import com.mulesoft.connectors.jev.internal.provider.CloudflareAdapter;
import com.mulesoft.connectors.jev.internal.provider.CostExtractor;
import com.mulesoft.connectors.jev.internal.provider.RequestIdExtractor;

/**
 * The Cloudflare Workers AI route: it runs the TypeSafe model through Cloudflare's {@code /ai/run} surface, which puts
 * the model id in the path and nests the decision under {@code input}. Cloudflare does not enumerate models, so
 * model-list operations report {@code JEV:UNSUPPORTED_BY_PROVIDER}. Cost is estimated from tokens.
 */
@Alias("cloudflare")
@DisplayName("Cloudflare Workers AI")
public class CloudflareConnectionProvider extends AbstractJevConnectionProvider {

  @Parameter
  @Placement(order = 1)
  @Summary("Cloudflare account id; forms the /ai/run base URL.")
  private String accountId;

  @Parameter
  @Password
  @DisplayName("API Token")
  @Placement(order = 2)
  @Summary("Cloudflare API token, sent as a Bearer credential.")
  private String apiToken;

  @Parameter
  @Optional(defaultValue = RouteDefaults.CLOUDFLARE_MODEL)
  @Placement(order = 3)
  @Summary("Default model used when an operation does not specify one.")
  private String model;

  @Parameter
  @Optional(defaultValue = "USER")
  @DisplayName("Token Scope")
  @Placement(tab = "Advanced", order = 10)
  @Summary("Whether the API token is a user token or an account token; selects the token-verify endpoint.")
  private TokenScope tokenScope;

  @Override
  public JevConnection connect() {
    CloudflareAdapter adapter = new CloudflareAdapter("cloudflare", RouteDefaults.cloudflareBaseUrl(accountId), model,
        Capabilities.full(false), apiToken, customHeaders(), CostExtractor.NONE, RequestIdExtractor.NONE, transport());
    return new JevConnection(adapter, fallbackAdapters());
  }

  @Override
  public ConnectionValidationResult validate(JevConnection connection) {
    return ConnectionValidationResult.success();
  }

  /** Whether a Cloudflare API token is scoped to a user or a single account. */
  public enum TokenScope {
    USER, ACCOUNT
  }
}
