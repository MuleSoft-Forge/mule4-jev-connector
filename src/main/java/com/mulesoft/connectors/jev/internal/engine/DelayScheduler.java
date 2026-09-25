package com.mulesoft.connectors.jev.internal.engine;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Schedules a delayed completion without blocking the calling (I/O) thread. Retries are composed on top of the returned
 * future, so no operation ever sleeps a runtime thread.
 */
@FunctionalInterface
public interface DelayScheduler {

  CompletableFuture<Void> after(long delayMs);

  /** Backs the scheduler with a {@link ScheduledExecutorService} (e.g. a Mule runtime scheduler). */
  static DelayScheduler on(ScheduledExecutorService executor) {
    return delayMs -> {
      if (delayMs <= 0) {
        return CompletableFuture.completedFuture(null);
      }
      CompletableFuture<Void> future = new CompletableFuture<>();
      executor.schedule(() -> future.complete(null), delayMs, TimeUnit.MILLISECONDS);
      return future;
    };
  }
}
