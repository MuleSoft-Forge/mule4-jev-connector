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
import com.mulesoft.connectors.jev.internal.stats.DecisionStatsRecorder;
import com.mulesoft.connectors.jev.internal.stats.DecisionStatsRecorder.SetStats;
import com.mulesoft.connectors.jev.internal.stats.DecisionStatsRecorder.Window;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Fires once when a monitored decision metric moves past its threshold versus a baseline window, and re-arms when every
 * metric is back inside its threshold. It reads the privacy-safe aggregates the {@link DecisionStatsRecorder} keeps —
 * no-match rate, mean confidence and the choice/level distribution — and compares the current window to either the
 * first recorded window or the previous full one. Distribution shift uses {@link DriftMath#jensenShannon}, bounded to
 * {@code [0, 1]}.
 */
@Alias("on-drift-detected")
@DisplayName("On Drift Detected")
@MediaType(value = MediaType.APPLICATION_JSON, strict = false)
public class DriftDetectedSource extends PollingSource<InputStream, Void> {

  /** Which window the current window is compared against. */
  public enum Baseline {
    FIRST_WINDOW, PREVIOUS_WINDOW
  }

  private static final long MIN_SAMPLE = 30;

  @Config
  private JevConfiguration config;

  @Connection
  private JevConnection connection;

  @Parameter
  @Optional
  @Summary("Only watch this question set; blank watches inline question sets.")
  private String questionSetId;

  @Parameter
  @Optional
  @Summary("Only measure distribution shift for this question id; blank uses every question.")
  private String questionId;

  @Parameter
  @Optional(defaultValue = "500")
  @Summary("Decisions per comparison window; align with the recorder's window for stable baselines.")
  private int windowSize;

  @Parameter
  @Optional(defaultValue = "FIRST_WINDOW")
  @Summary("Compare against the first recorded window or the previous full window.")
  private Baseline baseline;

  @Parameter
  @Optional(defaultValue = "0.10")
  @Summary("Fire when the no-match rate rises by more than this over the baseline.")
  private double maxNoMatchRateIncrease;

  @Parameter
  @Optional(defaultValue = "0.10")
  @Summary("Fire when mean confidence drops by more than this below the baseline.")
  private double maxMeanConfidenceDrop;

  @Parameter
  @Optional(defaultValue = "0.10")
  @Summary("Fire when the choice/level distribution shifts by more than this (Jensen-Shannon, 0-1).")
  private double maxDistributionShift;

  @Override
  protected void doStart() {
    // No resources to acquire.
  }

  @Override
  protected void doStop() {
    // Nothing to release.
  }

  @Override
  public void poll(PollContext<InputStream, Void> pollContext) {
    if (pollContext.isSourceStopping() || connection.stats() == null) {
      return;
    }
    SetStats stats = connection.stats().windowsFor(questionSetId);
    if (stats == null) {
      return;
    }
    Window current = stats.current();
    Window base = baseline == Baseline.PREVIOUS_WINDOW ? stats.previous() : stats.first();
    if (base == null || current == null || current.count() < Math.min(MIN_SAMPLE, windowSize)) {
      return;
    }

    Breach breach = evaluate(base, current);
    String key = "drift:" + (questionSetId == null ? "__inline__" : questionSetId);
    if (breach != null) {
      if (SourceSupport.isArmed(connection.stats().store(), key)) {
        SourceSupport.emit(pollContext, key + ":" + breach.metric + ":" + System.currentTimeMillis(), payload(breach),
            null);
        SourceSupport.setArmed(connection.stats().store(), key, false);
      }
    } else {
      SourceSupport.setArmed(connection.stats().store(), key, true);
    }
  }

  /** Returns the first breached metric (no-match, then confidence, then distribution), or {@code null} if healthy. */
  private Breach evaluate(Window base, Window current) {
    double noMatchIncrease = current.noMatchRate() - base.noMatchRate();
    if (noMatchIncrease > maxNoMatchRateIncrease) {
      return new Breach("NO_MATCH_RATE", base.noMatchRate(), current.noMatchRate());
    }
    double confidenceDrop = base.meanConfidence() - current.meanConfidence();
    if (confidenceDrop > maxMeanConfidenceDrop) {
      return new Breach("MEAN_CONFIDENCE", base.meanConfidence(), current.meanConfidence());
    }
    double shift = distributionShift(base, current);
    if (shift > maxDistributionShift) {
      return new Breach("DISTRIBUTION_SHIFT", 0.0, shift);
    }
    return null;
  }

  /** Largest Jensen–Shannon shift across the watched questions (or the single {@code questionId} when set). */
  private double distributionShift(Window base, Window current) {
    Map<String, Map<String, Long>> baseDist = base.distribution();
    Map<String, Map<String, Long>> currentDist = current.distribution();
    if (questionId != null && !questionId.isBlank()) {
      return DriftMath.jensenShannon(baseDist.getOrDefault(questionId, new HashMap<>()),
          currentDist.getOrDefault(questionId, new HashMap<>()));
    }
    double worst = 0.0;
    for (String id : currentDist.keySet()) {
      double shift = DriftMath.jensenShannon(baseDist.getOrDefault(id, new HashMap<>()), currentDist.get(id));
      worst = Math.max(worst, shift);
    }
    return worst;
  }

  private ObjectNode payload(Breach breach) {
    ObjectNode payload = Json.object();
    payload.put("metric", breach.metric);
    payload.put("baseline", breach.baseline);
    payload.put("current", breach.current);
    payload.put("windowSize", windowSize);
    if (questionSetId == null) {
      payload.putNull("questionSetId");
    } else {
      payload.put("questionSetId", questionSetId);
    }
    if (questionId == null) {
      payload.putNull("questionId");
    } else {
      payload.put("questionId", questionId);
    }
    payload.put("windowEnd", System.currentTimeMillis());
    return payload;
  }

  @Override
  public void onRejectedItem(Result<InputStream, Void> result, SourceCallbackContext callbackContext) {
    // A rejected drift notification is dropped; the source stays disarmed until the metric recovers and re-arms.
  }

  /** The metric that breached its threshold, with the baseline and current values that tripped it. */
  private static final class Breach {

    private final String metric;
    private final double baseline;
    private final double current;

    Breach(String metric, double baseline, double current) {
      this.metric = metric;
      this.baseline = baseline;
      this.current = current;
    }
  }
}
