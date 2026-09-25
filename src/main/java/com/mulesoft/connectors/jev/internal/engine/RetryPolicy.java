package com.mulesoft.connectors.jev.internal.engine;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.OptionalLong;
import java.util.function.DoubleSupplier;

/**
 * Retry timing that matches the official TypeSafe SDK defaults: retry HTTP 408, 429 and any 5xx (including 529) plus
 * connection errors and timeouts; two retries after the first attempt; backoff starting at 500 ms, doubling to a 5 s
 * cap, with up to 25% subtracted as jitter; honour {@code retry-after-ms} first, then {@code retry-after} (seconds or
 * HTTP date).
 */
public final class RetryPolicy {

  private final int maxRetries;
  private final long baseDelayMs;
  private final long maxDelayMs;
  private final double jitterFactor;
  private final DoubleSupplier random;

  public RetryPolicy() {
    this(2, 500L, 5000L, 0.25, Math::random);
  }

  public RetryPolicy(int maxRetries, long baseDelayMs, long maxDelayMs, double jitterFactor, DoubleSupplier random) {
    this.maxRetries = maxRetries;
    this.baseDelayMs = baseDelayMs;
    this.maxDelayMs = maxDelayMs;
    this.jitterFactor = jitterFactor;
    this.random = random;
  }

  public int maxRetries() {
    return maxRetries;
  }

  /** Whether a response with this status should be retried (before failover). */
  public boolean shouldRetry(int status) {
    return status == 408 || status == 429 || status >= 500;
  }

  /**
   * Backoff for the given retry number (1 = first retry). A server-provided delay, when present, wins and is not
   * jittered. Otherwise the delay is {@code base * 2^(n-1)}, capped at the maximum, with up to {@code jitterFactor} of
   * it subtracted.
   */
  public long delayMs(int retryNumber, OptionalLong serverDelayMs) {
    if (serverDelayMs.isPresent()) {
      return Math.max(0L, Math.min(serverDelayMs.getAsLong(), maxDelayMs * 4));
    }
    double exp = baseDelayMs * Math.pow(2, retryNumber - 1);
    double capped = Math.min(exp, maxDelayMs);
    double jitter = capped * jitterFactor * random.getAsDouble();
    return Math.round(capped - jitter);
  }

  /**
   * Parses the retry-after hints. {@code retry-after-ms} (milliseconds) is checked first; otherwise {@code retry-after}
   * is treated as a number of seconds or, failing that, an HTTP date relative to {@code now}.
   */
  public static OptionalLong parseRetryAfter(String retryAfterMs, String retryAfter, Instant now) {
    if (retryAfterMs != null && !retryAfterMs.isBlank()) {
      try {
        return OptionalLong.of(Math.max(0L, Long.parseLong(retryAfterMs.trim())));
      } catch (NumberFormatException ignored) {
        // fall through to retry-after
      }
    }
    if (retryAfter != null && !retryAfter.isBlank()) {
      String trimmed = retryAfter.trim();
      try {
        return OptionalLong.of(Math.max(0L, Long.parseLong(trimmed) * 1000L));
      } catch (NumberFormatException notSeconds) {
        try {
          ZonedDateTime when = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
          long ms = when.toInstant().toEpochMilli() - now.toEpochMilli();
          return OptionalLong.of(Math.max(0L, ms));
        } catch (RuntimeException notDate) {
          return OptionalLong.empty();
        }
      }
    }
    return OptionalLong.empty();
  }
}
