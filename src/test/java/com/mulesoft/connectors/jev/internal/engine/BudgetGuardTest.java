package com.mulesoft.connectors.jev.internal.engine;

import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.support.InMemoryObjectStore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BudgetGuardTest {

  private static final long HOUR = 3_600_000L;

  private final BudgetGuard guard = new BudgetGuard(new InMemoryObjectStore());

  @Test
  void noLimitsIsANoOp() {
    for (int i = 0; i < 100; i++) {
      assertDoesNotThrow(() -> guard.reserve(null, null, HOUR));
    }
    assertEquals(0, guard.snapshot(HOUR).calls());
  }

  @Test
  void reservesUpToTheCallLimitThenRefuses() {
    guard.reserve(3L, null, HOUR);
    guard.reserve(3L, null, HOUR);
    guard.reserve(3L, null, HOUR);
    ModuleException error = assertThrows(ModuleException.class, () -> guard.reserve(3L, null, HOUR));
    assertEquals(JevErrorType.BUDGET_EXCEEDED, error.getType());
    assertEquals(3, guard.snapshot(HOUR).calls());
  }

  @Test
  void refusesOnceTheInputTokenLimitIsReached() {
    guard.reserve(null, 1000L, HOUR);
    guard.recordUsage(600L, HOUR);
    // Still under the limit, so another call is allowed.
    guard.reserve(null, 1000L, HOUR);
    guard.recordUsage(600L, HOUR);
    // Now the window has 1200 >= 1000 tokens, so the next reservation is refused.
    ModuleException error = assertThrows(ModuleException.class, () -> guard.reserve(null, 1000L, HOUR));
    assertEquals(JevErrorType.BUDGET_EXCEEDED, error.getType());
    assertEquals(1200, guard.snapshot(HOUR).inputTokens());
  }

  @Test
  void resetsWhenTheWindowElapses() throws InterruptedException {
    long window = 50L;
    guard.reserve(1L, null, window);
    assertThrows(ModuleException.class, () -> guard.reserve(1L, null, window));
    Thread.sleep(70L);
    // A fresh window: the call is allowed again and the counter has reset.
    assertDoesNotThrow(() -> guard.reserve(1L, null, window));
    assertEquals(1, guard.snapshot(window).calls());
  }

  @Test
  void concurrentReservationsNeverExceedTheLimit() throws InterruptedException {
    int limit = 200;
    int threads = 16;
    java.util.concurrent.atomic.AtomicInteger granted = new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    java.util.List<Thread> workers = new java.util.ArrayList<>();
    for (int t = 0; t < threads; t++) {
      Thread worker = new Thread(() -> {
        awaitQuietly(start);
        for (int i = 0; i < 100; i++) {
          try {
            guard.reserve((long) limit, null, HOUR);
            granted.incrementAndGet();
          } catch (ModuleException expected) {
            // budget exhausted
          }
        }
      });
      workers.add(worker);
      worker.start();
    }
    start.countDown();
    for (Thread worker : workers) {
      worker.join();
    }
    assertEquals(limit, granted.get());
    assertEquals(limit, guard.snapshot(HOUR).calls());
  }

  private static void awaitQuietly(java.util.concurrent.CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
