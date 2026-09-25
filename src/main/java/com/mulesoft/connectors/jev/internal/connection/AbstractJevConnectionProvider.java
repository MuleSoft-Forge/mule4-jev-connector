package com.mulesoft.connectors.jev.internal.connection;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.HttpService;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.sdk.api.annotation.dsl.xml.ParameterDsl;
import org.mule.sdk.api.annotation.param.NullSafe;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.Parameter;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Placement;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.connectivity.CachedConnectionProvider;

import com.mulesoft.connectors.jev.internal.engine.DecisionEngine;
import com.mulesoft.connectors.jev.internal.engine.DelayScheduler;
import com.mulesoft.connectors.jev.internal.engine.RetryPolicy;
import com.mulesoft.connectors.jev.internal.http.HttpTransport;
import com.mulesoft.connectors.jev.internal.provider.ProviderAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * Shared base for every keyed Jev connection provider. It owns the Mule HTTP client lifecycle — created when the
 * configuration starts, stopped when it stops — and the transport parameters every route shares (timeouts, connection
 * pooling and custom headers). Subclasses add their own route, base URL and credentials and build the adapter in
 * {@link #connect()} on top of {@link #transport()}.
 *
 * <p>
 * The client is created once per configuration and shared across evaluations, so no operation ever opens a socket.
 */
public abstract class AbstractJevConnectionProvider
    implements
      CachedConnectionProvider<JevConnection>,
      org.mule.runtime.api.lifecycle.Startable,
      org.mule.runtime.api.lifecycle.Stoppable {

  @Inject
  private HttpService httpService;

  @Inject
  private SchedulerService schedulerService;

  @Parameter
  @Optional(defaultValue = "60000")
  @Placement(tab = "Advanced", order = 1)
  @Summary("Per-request response timeout in milliseconds.")
  private int responseTimeoutMs;

  @Parameter
  @Optional(defaultValue = "30000")
  @Placement(tab = "Advanced", order = 2)
  @Summary("Idle timeout in milliseconds before a pooled connection is closed.")
  private int connectionIdleTimeoutMs;

  @Parameter
  @Optional(defaultValue = "-1")
  @Placement(tab = "Advanced", order = 3)
  @Summary("Maximum concurrent connections, or -1 for unlimited.")
  private int maxConnections;

  @Parameter
  @Optional(defaultValue = "true")
  @Placement(tab = "Advanced", order = 4)
  @Summary("Reuse pooled connections across requests.")
  private boolean usePersistentConnections;

  @Parameter
  @Optional
  @NullSafe
  @DisplayName("Custom Headers")
  @Placement(tab = "Advanced", order = 5)
  @Summary("Headers added to every request, e.g. a gateway tenant or tracing header.")
  private Map<String, String> customHeaders;

  @Parameter
  @Optional
  @NullSafe
  @DisplayName("Fallback Routes")
  @Placement(tab = "Failover", order = 1)
  @ParameterDsl(allowReferences = false)
  @Summary("Ordered fallback routes tried when the primary fails with a connectivity, rate-limit, overload or "
      + "timeout error. Never used for validation or authorization failures.")
  private List<FallbackRoute> fallbacks;

  private HttpClient httpClient;
  private Scheduler scheduler;
  private DecisionEngine engine;

  @Override
  public void start() {
    HttpClientConfiguration configuration = new HttpClientConfiguration.Builder()
        .setName("jev-" + Integer.toHexString(System.identityHashCode(this)))
        .setTlsContextFactory(TlsContextFactory.builder().buildDefault()).setMaxConnections(maxConnections)
        .setUsePersistentConnections(usePersistentConnections).setConnectionIdleTimeout(connectionIdleTimeoutMs)
        .setStreaming(true).build();
    httpClient = httpService.getClientFactory().create(configuration);
    httpClient.start();
    // Injection into a connection provider is populated before start() (unlike a @Configuration), so the engine and
    // its retry scheduler are owned here and shared by every operation on this connection.
    scheduler = schedulerService.cpuLightScheduler();
    engine = new DecisionEngine(new RetryPolicy(), DelayScheduler.on(scheduler));
  }

  @Override
  public void stop() {
    if (httpClient != null) {
      httpClient.stop();
    }
    if (scheduler != null) {
      scheduler.stop();
    }
  }

  /** The shared decision engine, created in {@link #start()} once the runtime scheduler is available. */
  protected DecisionEngine engine() {
    return engine;
  }

  /** A transport bound to the shared, started HTTP client. */
  protected HttpTransport transport() {
    return new HttpTransport(httpClient, responseTimeoutMs);
  }

  /** Headers to attach to every request, never {@code null}. */
  protected Map<String, String> customHeaders() {
    return customHeaders == null ? Map.of() : new LinkedHashMap<>(customHeaders);
  }

  /** The configured fallback adapters, in order, each bound to the shared transport. Empty when none are configured. */
  protected List<ProviderAdapter> fallbackAdapters() {
    if (fallbacks == null || fallbacks.isEmpty()) {
      return List.of();
    }
    List<ProviderAdapter> adapters = new ArrayList<>(fallbacks.size());
    Map<String, String> headers = customHeaders();
    for (FallbackRoute fallback : fallbacks) {
      adapters.add(RouteAdapters.build(fallback, transport(), headers));
    }
    return adapters;
  }

  @Override
  public void disconnect(JevConnection connection) {
    // The HTTP client is owned by this provider and released in stop(); connections hold no sockets.
  }
}
