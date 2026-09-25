package com.mulesoft.connectors.jev.internal.connection;

/**
 * The route a connection (or a fallback within it) speaks to. Selecting one picks the adapter, base URL, default model
 * and cost/request-id conventions for that provider.
 */
public enum RouteType {

  TYPESAFE, OPENROUTER, VERCEL, CLOUDFLARE, COMPATIBLE;

  /** Maps a route name (as recorded in {@code attributes.provider}) back to its type, defaulting to compatible. */
  public static RouteType fromRouteName(String routeName) {
    if (routeName == null) {
      return COMPATIBLE;
    }
    for (RouteType type : values()) {
      if (type.name().equalsIgnoreCase(routeName)) {
        return type;
      }
    }
    return COMPATIBLE;
  }
}
