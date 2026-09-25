package com.mulesoft.connectors.jev.internal.http;

import org.mule.runtime.http.api.HttpConstants;
import org.mule.runtime.http.api.client.HttpClient;
import org.mule.runtime.http.api.client.HttpRequestOptions;
import org.mule.runtime.http.api.domain.entity.ByteArrayHttpEntity;
import org.mule.runtime.http.api.domain.entity.EmptyHttpEntity;
import org.mule.runtime.http.api.domain.message.request.HttpRequest;
import org.mule.runtime.http.api.domain.message.request.HttpRequestBuilder;
import org.mule.runtime.http.api.domain.message.response.HttpResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper over the Mule HTTP client that performs non-blocking sends and adapts the response to a SDK-free
 * {@link RawHttpResponse}. All runtime HTTP-API usage is confined here so the rest of the connector stays testable
 * against plain values.
 */
public final class HttpTransport {

  private final HttpClient httpClient;
  private final int responseTimeoutMs;

  public HttpTransport(HttpClient httpClient, int responseTimeoutMs) {
    this.httpClient = httpClient;
    this.responseTimeoutMs = responseTimeoutMs;
  }

  /**
   * Sends a request without blocking. The returned future completes with the response (any status), or completes
   * exceptionally on a transport failure (I/O, DNS, TLS, timeout).
   */
  public CompletableFuture<RawHttpResponse> send(HttpConstants.Method method, String url, Map<String, String> headers,
      byte[] body) {
    HttpRequestBuilder builder = HttpRequest.builder().method(method).uri(url)
        .entity(body == null ? new EmptyHttpEntity() : new ByteArrayHttpEntity(body));
    if (headers != null) {
      headers.forEach(builder::addHeader);
    }
    HttpRequest request = builder.build();

    HttpRequestOptions options = HttpRequestOptions.builder().responseTimeout(responseTimeoutMs).followsRedirect(false)
        .build();

    return httpClient.sendAsync(request, options).thenApply(HttpTransport::toRaw);
  }

  private static RawHttpResponse toRaw(HttpResponse response) {
    byte[] bytes;
    try {
      bytes = response.getEntity().getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read response body", e);
    }
    String bodyText = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    Map<String, String> headers = new HashMap<>();
    for (String name : response.getHeaderNames()) {
      headers.put(name, response.getHeaderValue(name));
    }
    return new RawHttpResponse(response.getStatusCode(), bodyText, headers);
  }
}
