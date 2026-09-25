package com.mulesoft.connectors.jev.internal.http;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A minimal, SDK-free view of an HTTP response: status, body text, and case-insensitive header lookup. Keeping this
 * decoupled from the Mule HTTP types lets the parsing and error-mapping code be unit-tested without a runtime.
 */
public final class RawHttpResponse {

  private final int status;
  private final String body;
  private final Map<String, String> headers;

  public RawHttpResponse(int status, String body, Map<String, String> headers) {
    this.status = status;
    this.body = body;
    Map<String, String> lower = new HashMap<>();
    if (headers != null) {
      headers.forEach((k, v) -> {
        if (k != null) {
          lower.put(k.toLowerCase(Locale.ROOT), v);
        }
      });
    }
    this.headers = Collections.unmodifiableMap(lower);
  }

  public int status() {
    return status;
  }

  public String body() {
    return body;
  }

  public boolean isSuccess() {
    return status >= 200 && status < 300;
  }

  /** First value of the named header, case-insensitive, or {@code null}. */
  public String header(String name) {
    return name == null ? null : headers.get(name.toLowerCase(Locale.ROOT));
  }
}
