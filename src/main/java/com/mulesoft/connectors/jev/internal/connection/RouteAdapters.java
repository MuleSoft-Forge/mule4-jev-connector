package com.mulesoft.connectors.jev.internal.connection;

import com.mulesoft.connectors.jev.internal.http.HttpTransport;
import com.mulesoft.connectors.jev.internal.provider.Capabilities;
import com.mulesoft.connectors.jev.internal.provider.CloudflareAdapter;
import com.mulesoft.connectors.jev.internal.provider.CostExtractor;
import com.mulesoft.connectors.jev.internal.provider.ProviderAdapter;
import com.mulesoft.connectors.jev.internal.provider.RequestIdExtractor;
import com.mulesoft.connectors.jev.internal.provider.SystemOneAdapter;

import java.util.Map;

/**
 * Builds a {@link ProviderAdapter} for an inline {@link FallbackRoute}, applying each route's standard base URL, model,
 * cost extractor and request-id convention when the definition omits them. Keeps the per-route wiring in one place so
 * the primary connection providers and the failover path agree on how each route behaves.
 */
public final class RouteAdapters {

  private RouteAdapters() {
  }

  /** Builds the adapter for a fallback definition, sharing the connection's transport and custom headers. */
  public static ProviderAdapter build(FallbackRoute route, HttpTransport transport, Map<String, String> customHeaders) {
    String model = route.getModel();
    switch (route.getRoute()) {
      case OPENROUTER :
        return new SystemOneAdapter("openrouter", orDefault(route.getBaseUrl(), RouteDefaults.OPENROUTER_BASE_URL),
            orDefault(model, RouteDefaults.OPENROUTER_MODEL), Capabilities.full(true), route.getApiKey(), customHeaders,
            CostExtractor.OPENROUTER, RequestIdExtractor.header("x-request-id"), transport);
      case VERCEL :
        return new SystemOneAdapter("vercel", orDefault(route.getBaseUrl(), RouteDefaults.VERCEL_BASE_URL),
            orDefault(model, RouteDefaults.VERCEL_MODEL), Capabilities.full(true), route.getApiKey(), customHeaders,
            CostExtractor.VERCEL, RequestIdExtractor.VERCEL_GENERATION_ID, transport);
      case CLOUDFLARE :
        return new CloudflareAdapter("cloudflare", cloudflareBaseUrl(route),
            orDefault(model, RouteDefaults.CLOUDFLARE_MODEL), Capabilities.full(false), route.getApiKey(),
            customHeaders, CostExtractor.NONE, RequestIdExtractor.NONE, transport);
      case COMPATIBLE :
        return new SystemOneAdapter("compatible", requireBaseUrl(route), model,
            Capabilities.full(route.isSupportsModelList()), route.getApiKey(), customHeaders, CostExtractor.NONE,
            RequestIdExtractor.header("x-typesafe-request-id"), transport);
      case TYPESAFE :
      default :
        return new SystemOneAdapter("typesafe", orDefault(route.getBaseUrl(), RouteDefaults.TYPESAFE_BASE_URL),
            orDefault(model, RouteDefaults.TYPESAFE_MODEL), Capabilities.full(true), route.getApiKey(), customHeaders,
            CostExtractor.NONE, RequestIdExtractor.header("x-typesafe-request-id"), transport);
    }
  }

  private static String cloudflareBaseUrl(FallbackRoute route) {
    if (route.getBaseUrl() != null && !route.getBaseUrl().isBlank()) {
      return route.getBaseUrl();
    }
    if (route.getAccountId() == null || route.getAccountId().isBlank()) {
      throw new IllegalArgumentException("A Cloudflare fallback requires either a base URL or an account id");
    }
    return RouteDefaults.cloudflareBaseUrl(route.getAccountId());
  }

  private static String requireBaseUrl(FallbackRoute route) {
    if (route.getBaseUrl() == null || route.getBaseUrl().isBlank()) {
      throw new IllegalArgumentException("A compatible fallback requires a base URL");
    }
    return route.getBaseUrl();
  }

  private static String orDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
