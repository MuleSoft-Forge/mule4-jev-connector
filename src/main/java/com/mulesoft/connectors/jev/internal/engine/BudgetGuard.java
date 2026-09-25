package com.mulesoft.connectors.jev.internal.engine;

import org.mule.runtime.api.store.ObjectStore;
import org.mule.runtime.api.store.ObjectStoreException;
import org.mule.runtime.api.store.ObjectStoreManager;
import org.mule.runtime.api.store.ObjectStoreSettings;
import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.jev.internal.error.JevErrorType;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces a spend ceiling across a rolling time window, cluster-wide. Before every billed call an operation
 * {@link #reserve reserves} one call; if that would push the window past its call limit — or if the window has already
 * passed its input-token limit — the guard raises {@code JEV:BUDGET_EXCEEDED} and no provider call is made. After the
 * call the operation {@link #recordUsage records} the actual input tokens so the token limit tightens over the window.
 *
 * <p>
 * The window counters live in a persistent, distributed Object Store so a limit holds across CloudHub 2.0 replicas and
 * survives a restart. Object-Store operations are not transactional, so a JVM lock serialises the read-modify-write to
 * keep a node's own concurrent batch fan-out exact; across the cluster the limit is enforced best-effort, which is the
 * documented contract.
 */
public final class BudgetGuard {

  private static final Logger LOGGER = LoggerFactory.getLogger(BudgetGuard.class);
  private static final String STORE_NAME = "_jevBudget";
  private static final String WINDOW_KEY = "window";

  private final ObjectStore<Serializable> store;
  private final Object lock = new Object();

  public BudgetGuard(ObjectStore<Serializable> store) {
    this.store = store;
  }

  /** Builds a shared, distributed, persistent budget store from the runtime's Object Store manager. */
  public static BudgetGuard create(ObjectStoreManager manager) {
    ObjectStore<Serializable> store = manager.getOrCreateObjectStore(STORE_NAME,
        ObjectStoreSettings.builder().persistent(true).build());
    return new BudgetGuard(store);
  }

  /**
   * Reserves one billed call in the current window, raising {@code JEV:BUDGET_EXCEEDED} when a configured limit would
   * be broken. A {@code null} limit means that dimension is unbounded. When both limits are {@code null} the guard is a
   * no-op.
   */
  public void reserve(Long maxCalls, Long maxInputTokens, long windowMillis) {
    if (maxCalls == null && maxInputTokens == null) {
      return;
    }
    synchronized (lock) {
      BudgetWindow window = currentWindow(windowMillis);
      if (maxCalls != null && window.calls + 1 > maxCalls) {
        throw new ModuleException(
            "Budget exceeded: " + window.calls + " of " + maxCalls + " calls used in the current window",
            JevErrorType.BUDGET_EXCEEDED);
      }
      if (maxInputTokens != null && window.inputTokens >= maxInputTokens) {
        throw new ModuleException("Budget exceeded: " + window.inputTokens + " of " + maxInputTokens
            + " input tokens used in the current window", JevErrorType.BUDGET_EXCEEDED);
      }
      window.calls++;
      persist(window);
    }
  }

  /** Adds the actual input tokens of a completed call to the current window. */
  public void recordUsage(Long inputTokens, long windowMillis) {
    if (inputTokens == null || inputTokens <= 0) {
      return;
    }
    synchronized (lock) {
      BudgetWindow window = currentWindow(windowMillis);
      window.inputTokens += inputTokens;
      persist(window);
    }
  }

  /** A read-only snapshot of the current window, for the budget-threshold source. Never {@code null}. */
  public Snapshot snapshot(long windowMillis) {
    synchronized (lock) {
      BudgetWindow window = currentWindow(windowMillis);
      return new Snapshot(window.windowStartMs, window.calls, window.inputTokens);
    }
  }

  /** Loads the stored window, resetting it when the window has elapsed. Caller holds {@link #lock}. */
  private BudgetWindow currentWindow(long windowMillis) {
    long now = System.currentTimeMillis();
    try {
      if (store.contains(WINDOW_KEY)) {
        BudgetWindow stored = (BudgetWindow) store.retrieve(WINDOW_KEY);
        if (now - stored.windowStartMs < windowMillis) {
          return stored;
        }
      }
    } catch (ObjectStoreException | RuntimeException e) {
      LOGGER.debug("Budget window read failed; starting a fresh window", e);
    }
    return new BudgetWindow(now, 0, 0);
  }

  private void persist(BudgetWindow window) {
    try {
      if (store.contains(WINDOW_KEY)) {
        store.remove(WINDOW_KEY);
      }
      store.store(WINDOW_KEY, window);
    } catch (ObjectStoreException | RuntimeException e) {
      LOGGER.warn("Budget window write failed; the limit may under-count until the next successful write", e);
    }
  }

  /** Immutable view of a window's usage. */
  public static final class Snapshot {

    private final long windowStartMs;
    private final long calls;
    private final long inputTokens;

    Snapshot(long windowStartMs, long calls, long inputTokens) {
      this.windowStartMs = windowStartMs;
      this.calls = calls;
      this.inputTokens = inputTokens;
    }

    public long windowStartMs() {
      return windowStartMs;
    }

    public long calls() {
      return calls;
    }

    public long inputTokens() {
      return inputTokens;
    }
  }

  /** The serializable per-window counter. */
  private static final class BudgetWindow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long windowStartMs;
    private long calls;
    private long inputTokens;

    BudgetWindow(long windowStartMs, long calls, long inputTokens) {
      this.windowStartMs = windowStartMs;
      this.calls = calls;
      this.inputTokens = inputTokens;
    }
  }
}
