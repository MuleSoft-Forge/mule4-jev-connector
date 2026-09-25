package com.mulesoft.connectors.jev.internal.connection;

import java.util.List;

/**
 * Per-route static defaults from {@code docs/provider-contracts.md}: base URLs and the model ids each route is known to
 * serve. These back both the fallback adapter factory (when an inline fallback omits a base URL or model) and the
 * design-time model value providers, which merge them with a live {@code list-models} call when possible.
 */
public final class RouteDefaults {

  public static final String TYPESAFE_BASE_URL = "https://api.typesafe.ai";
  public static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api";
  public static final String VERCEL_BASE_URL = "https://ai-gateway.vercel.sh/typesafe";

  public static final String TYPESAFE_MODEL = "jev-latest";
  public static final String OPENROUTER_MODEL = "~typesafe/jev-latest";
  public static final String VERCEL_MODEL = "typesafe-ai/jev";
  public static final String CLOUDFLARE_MODEL = "typesafe/jev";

  private RouteDefaults() {
  }

  /** The Cloudflare Workers AI {@code /ai/run} base URL for an account. */
  public static String cloudflareBaseUrl(String accountId) {
    return "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/run";
  }

  /** The static model ids known for a route, in the order they should be offered. Empty when the route is free-text. */
  public static List<String> models(RouteType route) {
    switch (route) {
      case TYPESAFE :
        return List.of("jev-latest", "jev-1.13.0");
      case OPENROUTER :
        return List.of("~typesafe/jev-latest", "typesafe/jev-1.13");
      case VERCEL :
        return List.of("typesafe-ai/jev");
      case CLOUDFLARE :
        return List.of("typesafe/jev");
      default :
        return List.of();
    }
  }
}
