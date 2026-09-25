package com.mulesoft.connectors.jev.internal.operation;

import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.param.Connection;
import org.mule.sdk.api.annotation.param.display.DisplayName;

import com.mulesoft.connectors.jev.internal.connection.JevConnection;
import com.mulesoft.connectors.jev.internal.provider.Capabilities;

/**
 * Local utility operations that answer from connection metadata rather than a billed provider call.
 */
public class UtilityOperations {

  /**
   * Returns the capabilities of the connected primary route (Noul/Choice/Score support, confidence, model-list support
   * and the option/level ceilings). Makes no provider call.
   *
   * @param connection
   *          the resolved Jev connection.
   * @return the primary route's capabilities.
   */
  @Alias("get-capabilities")
  @DisplayName("[Util] Get Capabilities")
  public Capabilities getCapabilities(@Connection JevConnection connection) {
    return connection.primary().capabilities();
  }
}
