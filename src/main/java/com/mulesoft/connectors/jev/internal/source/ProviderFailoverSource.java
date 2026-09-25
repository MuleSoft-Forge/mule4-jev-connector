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
import com.mulesoft.connectors.jev.internal.util.Json;

import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Emits one item per failover recorded since the last poll: a request the primary route could not serve was answered by
 * a fallback. Unlike the threshold sources this is an event stream, so it uses the poll watermark (the last event's
 * timestamp) to deliver each failover exactly once and survive a restart.
 */
@Alias("on-provider-failover")
@DisplayName("On Provider Failover")
@MediaType(value = MediaType.APPLICATION_JSON, strict = false)
public class ProviderFailoverSource extends PollingSource<InputStream, Void> {

  @Config
  private JevConfiguration config;

  @Connection
  private JevConnection connection;

  @Parameter
  @Optional(defaultValue = "true")
  @Summary("Reserved for emitting route-recovery events once recoveries are tracked; failovers always emit.")
  private boolean includeRecoveries;

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
    long since = pollContext.getWatermark().filter(Long.class::isInstance).map(Long.class::cast).orElse(0L);
    List<DecisionStatsRecorder.FailoverEvent> events = connection.stats().failoverEventsSince(since);
    for (DecisionStatsRecorder.FailoverEvent event : events) {
      if (pollContext.isSourceStopping()) {
        return;
      }
      SourceSupport.emit(pollContext, event.from() + "->" + event.to() + "@" + event.timestamp(), payload(event),
          event.timestamp());
    }
  }

  private ObjectNode payload(DecisionStatsRecorder.FailoverEvent event) {
    ObjectNode payload = Json.object();
    payload.put("from", event.from());
    payload.put("to", event.to());
    payload.put("reason", "FAILOVER");
    payload.putNull("errorType");
    payload.put("timestamp", event.timestamp());
    return payload;
  }

  @Override
  public void onRejectedItem(Result<InputStream, Void> result, SourceCallbackContext callbackContext) {
    // A rejected failover event is dropped; the watermark still advances so it is not re-emitted.
  }
}
