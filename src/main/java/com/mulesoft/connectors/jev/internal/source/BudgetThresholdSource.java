package com.mulesoft.connectors.jev.internal.source;

import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.param.Config;
import org.mule.sdk.api.annotation.param.Connection;
import org.mule.sdk.api.annotation.param.MediaType;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.Parameter;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.runtime.operation.Result;
import org.mule.sdk.api.runtime.source.PollContext;
import org.mule.sdk.api.runtime.source.PollingSource;
import org.mule.sdk.api.runtime.source.SourceCallbackContext;

import com.mulesoft.connectors.jev.internal.config.JevConfiguration;
import com.mulesoft.connectors.jev.internal.connection.JevConnection;
import com.mulesoft.connectors.jev.internal.engine.BudgetGuard;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Fires once when usage in the current budget window crosses {@code percent} of a configured limit, and re-arms when
 * usage falls back inside the threshold (which also happens when the window resets). It reads the same
 * {@link BudgetGuard} counters the operations enforce, so the signal and the enforcement never diverge.
 */
@Alias("on-budget-threshold")
@DisplayName("On Budget Threshold")
@MediaType(value = MediaType.APPLICATION_JSON, strict = false)
public class BudgetThresholdSource extends PollingSource<InputStream, Void> {

  /** Which budget dimension to watch. */
  public enum BudgetMetric {
    CALLS, INPUT_TOKENS
  }

  @Config
  private JevConfiguration config;

  @Connection
  private JevConnection connection;

  @Parameter
  @Optional(defaultValue = "80")
  @Summary("Fire when usage reaches this percent of the configured limit.")
  private int percent;

  @Parameter
  @Optional(defaultValue = "CALLS")
  @Summary("The budget dimension to watch.")
  private BudgetMetric metric;

  @Override
  protected void doStart() {
    // No resources to acquire; state lives in the stats Object Store.
  }

  @Override
  protected void doStop() {
    // Nothing to release.
  }

  @Override
  public void poll(PollContext<InputStream, Void> pollContext) {
    if (pollContext.isSourceStopping()) {
      return;
    }
    BudgetGuard budget = connection.budget();
    if (budget == null || !config.isBudgetEnabled() || connection.stats() == null) {
      return;
    }
    Long limit = metric == BudgetMetric.CALLS
        ? config.getBudgetMaxCallsPerWindow()
        : config.getBudgetMaxInputTokensPerWindow();
    if (limit == null || limit <= 0) {
      return;
    }

    BudgetGuard.Snapshot snapshot = budget.snapshot(config.budgetWindowMillis());
    long used = metric == BudgetMetric.CALLS ? snapshot.calls() : snapshot.inputTokens();
    double usedPercent = 100.0 * used / limit;
    String key = "budget:" + metric;

    if (usedPercent >= percent) {
      if (SourceSupport.isArmed(connection.stats().store(), key)) {
        SourceSupport.emit(pollContext, key + ":" + snapshot.windowStartMs(),
            payload(snapshot, used, limit, usedPercent), null);
        SourceSupport.setArmed(connection.stats().store(), key, false);
      }
    } else {
      SourceSupport.setArmed(connection.stats().store(), key, true);
    }
  }

  private ObjectNode payload(BudgetGuard.Snapshot snapshot, long used, long limit, double usedPercent) {
    ObjectNode payload = Json.object();
    payload.put("metric", metric.name());
    payload.put("used", used);
    payload.put("limit", limit);
    payload.put("percent", Math.round(usedPercent));
    payload.put("threshold", percent);
    BigDecimal price = config.getPricePerMillionInputTokens();
    if (price != null) {
      BigDecimal cost = price.multiply(BigDecimal.valueOf(snapshot.inputTokens()))
          .divide(BigDecimal.valueOf(1_000_000L), 6, RoundingMode.HALF_UP);
      payload.put("estimatedCostUsd", cost);
    } else {
      payload.putNull("estimatedCostUsd");
    }
    payload.put("windowStart", snapshot.windowStartMs());
    return payload;
  }

  @Override
  public void onRejectedItem(Result<InputStream, Void> result, SourceCallbackContext callbackContext) {
    // A rejected budget notification is dropped; the next breach re-emits from the live counters.
  }
}
