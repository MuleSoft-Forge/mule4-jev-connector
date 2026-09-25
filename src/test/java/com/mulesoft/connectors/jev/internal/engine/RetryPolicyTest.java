package com.mulesoft.connectors.jev.internal.engine;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

  private final RetryPolicy policy = new RetryPolicy(2, 500L, 5000L, 0.25, () -> 0.0);

  @Test
  void retriesTransientStatusesOnly() {
    assertTrue(policy.shouldRetry(408));
    assertTrue(policy.shouldRetry(429));
    assertTrue(policy.shouldRetry(500));
    assertTrue(policy.shouldRetry(529));
    assertFalse(policy.shouldRetry(400));
    assertFalse(policy.shouldRetry(401));
    assertFalse(policy.shouldRetry(404));
  }

  @Test
  void backoffDoublesAndCaps() {
    assertEquals(500L, policy.delayMs(1, OptionalLong.empty()));
    assertEquals(1000L, policy.delayMs(2, OptionalLong.empty()));
    assertEquals(2000L, policy.delayMs(3, OptionalLong.empty()));
    // 500 * 2^4 = 8000, capped at 5000.
    assertEquals(5000L, policy.delayMs(5, OptionalLong.empty()));
  }

  @Test
  void jitterSubtractsUpToFactor() {
    RetryPolicy jittered = new RetryPolicy(2, 500L, 5000L, 0.25, () -> 1.0);
    // 500 - (500 * 0.25 * 1.0) = 375.
    assertEquals(375L, jittered.delayMs(1, OptionalLong.empty()));
  }

  @Test
  void serverDelayWinsAndIsNotJittered() {
    RetryPolicy jittered = new RetryPolicy(2, 500L, 5000L, 0.25, () -> 1.0);
    assertEquals(1234L, jittered.delayMs(1, OptionalLong.of(1234L)));
  }

  @Test
  void parsesRetryAfterMillisFirst() {
    assertEquals(OptionalLong.of(2500L), RetryPolicy.parseRetryAfter("2500", "9", Instant.now()));
  }

  @Test
  void parsesRetryAfterSeconds() {
    assertEquals(OptionalLong.of(3000L), RetryPolicy.parseRetryAfter(null, "3", Instant.now()));
  }

  @Test
  void parsesRetryAfterHttpDate() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    String httpDate = DateTimeFormatter.RFC_1123_DATE_TIME
        .format(ZonedDateTime.ofInstant(now.plusSeconds(10), java.time.ZoneOffset.UTC));
    OptionalLong parsed = RetryPolicy.parseRetryAfter(null, httpDate, now);
    assertTrue(parsed.isPresent());
    assertEquals(10000L, parsed.getAsLong());
  }

  @Test
  void returnsEmptyWhenNoHint() {
    assertTrue(RetryPolicy.parseRetryAfter(null, null, Instant.now()).isEmpty());
    assertTrue(RetryPolicy.parseRetryAfter("", "  ", Instant.now()).isEmpty());
  }
}
