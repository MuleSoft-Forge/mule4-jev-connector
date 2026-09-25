package com.mulesoft.connectors.jev.internal.connection;

import org.mule.runtime.api.tls.TlsContextFactory;
import org.mule.runtime.http.api.HttpService;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpClientConfiguration;
import org.mule.sdk.api.annotation.param.NullSafe;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.Parameter;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Placement;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.connectivity.CachedConnectionProvider;

import com.mulesoft.connectors.jev.internal.http.HttpTransport;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.inject.Inject;

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

  private HttpClient httpClient;

  @Override
  public void start() {
    HttpClientConfiguration configuration = new HttpClientConfiguration.Builder()
        .setName("jev-" + Integer.toHexString(System.identityHashCode(this)))
        .setTlsContextFactory(TlsContextFactory.builder().buildDefault()).setMaxConnections(maxConnections)
        .setUsePersistentConnections(usePersistentConnections).setConnectionIdleTimeout(connectionIdleTimeoutMs)
        .setStreaming(true).build();
    httpClient = httpService.getClientFactory().create(configuration);
    httpClient.start();
  }

  @Override
  public void stop() {
    if (httpClient != null) {
      httpClient.stop();
    }
  }

  /** A transport bound to the shared, started HTTP client. */
  protected HttpTransport transport() {
    return new HttpTransport(httpClient, responseTimeoutMs);
  }

  /** Headers to attach to every request, never {@code null}. */
  protected Map<String, String> customHeaders() {
    return customHeaders == null ? Map.of() : new LinkedHashMap<>(customHeaders);
  }

  @Override
  public void disconnect(JevConnection connection) {
    // The HTTP client is owned by this provider and released in stop(); connections hold no sockets.
  }
}
