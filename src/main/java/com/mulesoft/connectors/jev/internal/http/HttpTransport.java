package com.mulesoft.connectors.jev.internal.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.mule.sdk.api.http.HttpConstants;
import org.mule.sdk.api.http.HttpService;
import org.mule.sdk.api.http.client.HttpClient;
import org.mule.sdk.api.http.domain.entity.HttpEntityFactory;
import org.mule.sdk.api.http.domain.message.request.HttpRequest;
import org.mule.sdk.api.http.domain.message.request.HttpRequestBuilder;
import org.mule.sdk.api.http.domain.message.response.HttpResponse;

/**
 * Thin wrapper over the Mule HTTP client that performs non-blocking sends and adapts the response to
 * a SDK-free {@link RawHttpResponse}. All sdk-api HTTP usage is confined here.
 */
public final class HttpTransport {

  private final HttpService httpService;
  private final HttpClient httpClient;
  private final int responseTimeoutMs;

  public HttpTransport(HttpService httpService, HttpClient httpClient, int responseTimeoutMs) {
    this.httpService = httpService;
    this.httpClient = httpClient;
    this.responseTimeoutMs = responseTimeoutMs;
  }

  /**
   * Sends a request without blocking. The returned future completes with the response (any status),
   * or completes exceptionally on a transport failure (I/O, DNS, TLS, timeout).
   */
  public CompletableFuture<RawHttpResponse> send(HttpConstants.Method method, String url,
                                                 Map<String, String> headers, byte[] body) {
    HttpEntityFactory entityFactory = httpService.entityFactory();
    HttpRequestBuilder builder = httpService.requestBuilder()
        .method(method)
        .uri(url)
        .entity(body == null ? entityFactory.emptyEntity() : entityFactory.from(body));
    if (headers != null) {
      headers.forEach(builder::addHeader);
    }
    HttpRequest request = builder.build();

    return httpClient
        .sendAsync(request, options -> options.setResponseTimeout(responseTimeoutMs).setFollowsRedirect(false))
        .thenApply(HttpTransport::toRaw);
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
