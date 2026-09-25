package com.mulesoft.connectors.jev.internal.provider;

import java.io.Serializable;

/**
 * A route paired with its capabilities, as returned by {@code get-capabilities}. The list has the primary route first,
 * then each fallback in order, so a flow can inspect what every reachable route supports without a billed call.
 */
public class RouteCapabilities implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String route;
  private final boolean primary;
  private final Capabilities capabilities;

  public RouteCapabilities(String route, boolean primary, Capabilities capabilities) {
    this.route = route;
    this.primary = primary;
    this.capabilities = capabilities;
  }

  public String getRoute() {
    return route;
  }

  public boolean isPrimary() {
    return primary;
  }

  public Capabilities getCapabilities() {
    return capabilities;
  }
}
