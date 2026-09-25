package com.mulesoft.connectors.jev.internal.connection;

import com.mulesoft.connectors.jev.internal.provider.ProviderAdapter;

import java.util.List;

/**
 * Immutable, thread-safe connection handed to operations. It holds the resolved primary adapter and any fallback
 * adapters used for failover. It deliberately does not expose the raw HTTP client.
 */
public final class JevConnection {

  private final ProviderAdapter primary;
  private final List<ProviderAdapter> fallbacks;

  public JevConnection(ProviderAdapter primary, List<ProviderAdapter> fallbacks) {
    this.primary = primary;
    this.fallbacks = List.copyOf(fallbacks);
  }

  public ProviderAdapter primary() {
    return primary;
  }

  public List<ProviderAdapter> fallbacks() {
    return fallbacks;
  }
}
