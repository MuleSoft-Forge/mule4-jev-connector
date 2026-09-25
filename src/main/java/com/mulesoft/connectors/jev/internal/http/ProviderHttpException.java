package com.mulesoft.connectors.jev.internal.http;

import java.util.OptionalLong;

import com.mulesoft.connectors.jev.internal.error.JevErrorType;

/**
 * Thrown by an adapter when a route returns a non-2xx response. It carries the status and any
 * server-provided retry delay so the engine can decide whether to retry, fail over, or raise the
 * mapped {@code JEV:*} error.
 */
public final class ProviderHttpException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient int status;
  private final transient String body;
  private final transient OptionalLong retryAfterMs;

  public ProviderHttpException(int status, String body, OptionalLong retryAfterMs) {
    super("HTTP " + status);
    this.status = status;
    this.body = body;
    this.retryAfterMs = retryAfterMs == null ? OptionalLong.empty() : retryAfterMs;
  }

  public int status() {
    return status;
  }

  public String body() {
    return body;
  }

  public OptionalLong retryAfterMs() {
    return retryAfterMs;
  }

  public JevErrorType errorType() {
    return HttpErrorMapper.classify(status);
  }
}
