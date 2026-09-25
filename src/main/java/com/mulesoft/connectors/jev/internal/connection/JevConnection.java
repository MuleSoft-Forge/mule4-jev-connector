package com.mulesoft.connectors.jev.internal.connection;

import com.mulesoft.connectors.jev.internal.engine.DecisionEngine;
import com.mulesoft.connectors.jev.internal.provider.ProviderAdapter;

import java.util.List;

/**
 * Immutable, thread-safe connection handed to operations. It holds the resolved primary adapter, any fallback adapters
 * used for failover, and the shared {@link DecisionEngine} whose retry scheduler is a runtime service. It deliberately
 * does not expose the raw HTTP client.
 */
public final class JevConnection {

  private final ProviderAdapter primary;
  private final List<ProviderAdapter> fallbacks;
  private final DecisionEngine engine;

  public JevConnection(ProviderAdapter primary, List<ProviderAdapter> fallbacks, DecisionEngine engine) {
    this.primary = primary;
    this.fallbacks = List.copyOf(fallbacks);
    this.engine = engine;
  }

  /** Convenience for tests that drive the engine directly and never route through an operation. */
  public JevConnection(ProviderAdapter primary, List<ProviderAdapter> fallbacks) {
    this(primary, fallbacks, null);
  }

  public ProviderAdapter primary() {
    return primary;
  }

  public List<ProviderAdapter> fallbacks() {
    return fallbacks;
  }

  /** The shared engine built by the connection provider, or {@code null} when constructed directly in a test. */
  public DecisionEngine engine() {
    return engine;
  }
}
