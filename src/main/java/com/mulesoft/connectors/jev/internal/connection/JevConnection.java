package com.mulesoft.connectors.jev.internal.connection;

import com.mulesoft.connectors.jev.internal.cache.DecisionCache;
import com.mulesoft.connectors.jev.internal.engine.BudgetGuard;
import com.mulesoft.connectors.jev.internal.engine.DecisionEngine;
import com.mulesoft.connectors.jev.internal.provider.ProviderAdapter;
import com.mulesoft.connectors.jev.internal.stats.DecisionStatsRecorder;

import java.util.List;

/**
 * Immutable, thread-safe connection handed to operations. It holds the resolved primary adapter, any fallback adapters
 * used for failover, the shared {@link DecisionEngine} whose retry scheduler is a runtime service, and the
 * config-scoped governance objects (cache, budget guard and stats recorder) that wrap the runtime Object Stores. It
 * deliberately does not expose the raw HTTP client.
 */
public final class JevConnection {

  private final ProviderAdapter primary;
  private final List<ProviderAdapter> fallbacks;
  private final DecisionEngine engine;
  private final DecisionCache cache;
  private final BudgetGuard budget;
  private final DecisionStatsRecorder stats;

  public JevConnection(ProviderAdapter primary, List<ProviderAdapter> fallbacks, DecisionEngine engine,
      DecisionCache cache, BudgetGuard budget, DecisionStatsRecorder stats) {
    this.primary = primary;
    this.fallbacks = List.copyOf(fallbacks);
    this.engine = engine;
    this.cache = cache;
    this.budget = budget;
    this.stats = stats;
  }

  public JevConnection(ProviderAdapter primary, List<ProviderAdapter> fallbacks, DecisionEngine engine) {
    this(primary, fallbacks, engine, null, null, null);
  }

  /** Convenience for tests that drive the engine directly and never route through an operation. */
  public JevConnection(ProviderAdapter primary, List<ProviderAdapter> fallbacks) {
    this(primary, fallbacks, null, null, null, null);
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

  /** The decision cache, or {@code null} when caching infrastructure is absent (e.g. a direct-engine test). */
  public DecisionCache cache() {
    return cache;
  }

  /** The budget guard, or {@code null} when governance infrastructure is absent. */
  public BudgetGuard budget() {
    return budget;
  }

  /** The stats recorder, or {@code null} when monitoring infrastructure is absent. */
  public DecisionStatsRecorder stats() {
    return stats;
  }
}
