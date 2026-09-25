package com.mulesoft.connectors.jev.internal.config;

import org.mule.sdk.api.annotation.Configuration;
import org.mule.sdk.api.annotation.Operations;
import org.mule.sdk.api.annotation.connectivity.ConnectionProviders;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.Parameter;
import org.mule.sdk.api.annotation.param.display.Placement;
import org.mule.sdk.api.annotation.param.display.Summary;

import com.mulesoft.connectors.jev.internal.connection.CloudflareConnectionProvider;
import com.mulesoft.connectors.jev.internal.connection.CompatibleConnectionProvider;
import com.mulesoft.connectors.jev.internal.connection.MockConnectionProvider;
import com.mulesoft.connectors.jev.internal.connection.OpenRouterConnectionProvider;
import com.mulesoft.connectors.jev.internal.connection.TypeSafeConnectionProvider;
import com.mulesoft.connectors.jev.internal.connection.VercelConnectionProvider;
import com.mulesoft.connectors.jev.internal.operation.DecisionOperations;
import com.mulesoft.connectors.jev.internal.operation.UtilityOperations;

import java.math.BigDecimal;

/**
 * The single {@code <jev:config>} global element. It holds behaviour (defaults, cache, budget, monitoring); its
 * connection provider holds transport (route, credentials, HTTP).
 *
 * <p>
 * M0 wires the {@code mock} connection provider and the utility operations. Later milestones add the decision, batch
 * and policy operations and the cache / budget / stats object stores.
 */
@Configuration(name = "config")
@ConnectionProviders({TypeSafeConnectionProvider.class, OpenRouterConnectionProvider.class,
    VercelConnectionProvider.class, CloudflareConnectionProvider.class, CompatibleConnectionProvider.class,
    MockConnectionProvider.class})
@Operations({DecisionOperations.class, UtilityOperations.class})
public class JevConfiguration {

  @Parameter
  @Optional(defaultValue = "questions/")
  @Placement(tab = "General")
  @Summary("Classpath folder scanned by the question-set value provider.")
  private String defaultQuestionSetsLocation;

  @Parameter
  @Optional(defaultValue = "true")
  @Placement(tab = "General")
  @Summary("If false, missing capability fields become null with a WARN instead of failing.")
  private boolean failOnUnsupportedCapability;

  @Parameter
  @Optional(defaultValue = "false")
  @Placement(tab = "Cache")
  @Summary("Cache decisions keyed by a SHA-256 of route + model + state + questions.")
  private boolean cacheEnabled;

  @Parameter
  @Optional(defaultValue = "0.042")
  @Placement(tab = "Budget")
  @Summary("Price per million input tokens, used only for cost estimates.")
  private BigDecimal pricePerMillionInputTokens;

  @Parameter
  @Optional(defaultValue = "true")
  @Placement(tab = "Monitoring")
  @Summary("Feed drift and budget sources; stores counts and histograms, never state text.")
  private boolean statsEnabled;

  public String getDefaultQuestionSetsLocation() {
    return defaultQuestionSetsLocation;
  }

  public boolean isFailOnUnsupportedCapability() {
    return failOnUnsupportedCapability;
  }

  public boolean isCacheEnabled() {
    return cacheEnabled;
  }

  public BigDecimal getPricePerMillionInputTokens() {
    return pricePerMillionInputTokens;
  }

  public boolean isStatsEnabled() {
    return statsEnabled;
  }
}
