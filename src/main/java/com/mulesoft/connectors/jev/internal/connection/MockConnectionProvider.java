package com.mulesoft.connectors.jev.internal.connection;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.Parameter;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.connectivity.CachedConnectionProvider;
import org.mule.sdk.api.connectivity.ConnectionValidationResult;

import com.mulesoft.connectors.jev.internal.engine.DecisionEngine;
import com.mulesoft.connectors.jev.internal.engine.DelayScheduler;
import com.mulesoft.connectors.jev.internal.engine.RetryPolicy;
import com.mulesoft.connectors.jev.internal.provider.MockAdapter;

import java.util.List;

import javax.inject.Inject;

/**
 * Keyless connection provider that answers from in-process fixtures. It lets tests, the demo app and design-time
 * tooling exercise every operation without a provider key. It holds no transport, so it does not share the HTTP client
 * lifecycle of the keyed providers, but it does own the shared decision engine (and its runtime retry scheduler) the
 * same way.
 */
@Alias("mock")
@DisplayName("Mock (testing)")
public class MockConnectionProvider
    implements
      CachedConnectionProvider<JevConnection>,
      org.mule.runtime.api.lifecycle.Startable,
      org.mule.runtime.api.lifecycle.Stoppable {

  @Inject
  private SchedulerService schedulerService;

  private Scheduler scheduler;
  private DecisionEngine engine;

  @Parameter
  @Optional
  @Summary("Classpath folder holding canned JSON decision responses.")
  private String fixturesLocation;

  @Parameter
  @Optional(defaultValue = "0.5")
  @Summary("Noul probability returned when a fixture does not specify one.")
  private double defaultNoul;

  @Parameter
  @Optional(defaultValue = "0")
  @Summary("Artificial latency in milliseconds, to exercise timeout and retry paths.")
  private long latencyMs;

  @Override
  public void start() {
    scheduler = schedulerService.cpuLightScheduler();
    engine = new DecisionEngine(new RetryPolicy(), DelayScheduler.on(scheduler));
  }

  @Override
  public void stop() {
    if (scheduler != null) {
      scheduler.stop();
    }
  }

  @Override
  public JevConnection connect() {
    return new JevConnection(new MockAdapter(defaultNoul, latencyMs), List.of(), engine);
  }

  @Override
  public void disconnect(JevConnection connection) {
    // Nothing to release: the mock route holds no transport resources.
  }

  @Override
  public ConnectionValidationResult validate(JevConnection connection) {
    return ConnectionValidationResult.success();
  }
}
