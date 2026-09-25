package com.mulesoft.connectors.jev.internal.operation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Runs a list of asynchronous tasks with a bounded number in flight at once, without ever blocking a thread. It
 * launches up to {@code maxConcurrency} tasks; each completion launches the next, so at most {@code maxConcurrency} run
 * concurrently. Synchronous completions (as the {@code mock} route produces) are drained in a loop rather than by
 * recursion, so a large batch never overflows the stack. Results are returned in task order.
 *
 * <p>
 * Tasks are expected to be total — to capture their own failures in the result value rather than completing
 * exceptionally. If a task future does complete exceptionally the aggregate fails with that cause.
 */
final class BoundedFanout {

  private BoundedFanout() {
  }

  static <R> CompletableFuture<List<R>> run(int maxConcurrency, List<Supplier<CompletableFuture<R>>> tasks) {
    int count = tasks.size();
    List<R> results = new ArrayList<>(Collections.nCopies(count, null));
    CompletableFuture<List<R>> aggregate = new CompletableFuture<>();
    if (count == 0) {
      aggregate.complete(results);
      return aggregate;
    }
    Driver<R> driver = new Driver<>(tasks, results, aggregate);
    int slots = Math.max(1, Math.min(maxConcurrency, count));
    for (int i = 0; i < slots; i++) {
      driver.drive();
    }
    return aggregate;
  }

  /** Owns the shared launch cursor and drains synchronous completions iteratively. */
  private static final class Driver<R> {

    private final List<Supplier<CompletableFuture<R>>> tasks;
    private final List<R> results;
    private final CompletableFuture<List<R>> aggregate;
    private final int count;
    private final AtomicInteger next = new AtomicInteger(0);
    private final AtomicInteger remaining;

    Driver(List<Supplier<CompletableFuture<R>>> tasks, List<R> results, CompletableFuture<List<R>> aggregate) {
      this.tasks = tasks;
      this.results = results;
      this.aggregate = aggregate;
      this.count = tasks.size();
      this.remaining = new AtomicInteger(count);
    }

    void drive() {
      while (true) {
        int index = next.getAndIncrement();
        if (index >= count) {
          return;
        }
        CompletableFuture<R> future;
        try {
          future = tasks.get(index).get();
          if (future == null) {
            future = CompletableFuture.completedFuture(null);
          }
        } catch (RuntimeException e) {
          future = CompletableFuture.failedFuture(e);
        }
        if (future.isDone()) {
          if (!settle(index, future) || !advance()) {
            return;
          }
          // Otherwise loop to launch the next task on this thread, avoiding recursion on synchronous completions.
        } else {
          final CompletableFuture<R> pending = future;
          future.whenComplete((value, error) -> {
            if (settle(index, pending)) {
              if (advance()) {
                drive();
              }
            }
          });
          return;
        }
      }
    }

    /** Records the task's result; returns false (and fails the aggregate) if the task completed exceptionally. */
    private boolean settle(int index, CompletableFuture<R> future) {
      try {
        results.set(index, future.join());
        return true;
      } catch (RuntimeException e) {
        aggregate.completeExceptionally(e.getCause() != null ? e.getCause() : e);
        return false;
      }
    }

    /**
     * Decrements the outstanding count; completes the aggregate on the last task and signals whether to keep driving.
     */
    private boolean advance() {
      if (remaining.decrementAndGet() == 0) {
        aggregate.complete(results);
        return false;
      }
      return true;
    }
  }
}
