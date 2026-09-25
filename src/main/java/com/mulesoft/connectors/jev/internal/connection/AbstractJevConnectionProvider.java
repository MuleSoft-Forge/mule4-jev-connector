package com.mulesoft.connectors.jev.internal.connection;

import org.mule.sdk.api.connectivity.CachedConnectionProvider;

/**
 * Shared base for every Jev connection provider. A connection is cached per configuration.
 *
 * <p>
 * M0 establishes the type only. Milestone M1 moves the Mule HTTP client lifecycle here (created in {@code start()},
 * stopped in {@code stop()}), along with the proxy, TLS and timeout parameters that every route shares.
 */
public abstract class AbstractJevConnectionProvider implements CachedConnectionProvider<JevConnection> {
}
