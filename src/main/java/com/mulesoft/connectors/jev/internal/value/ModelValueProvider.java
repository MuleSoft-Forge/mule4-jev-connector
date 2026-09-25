package com.mulesoft.connectors.jev.internal.value;

import org.mule.sdk.api.annotation.param.Connection;
import org.mule.sdk.api.values.Value;
import org.mule.sdk.api.values.ValueBuilder;
import org.mule.sdk.api.values.ValueProvider;
import org.mule.sdk.api.values.ValueResolvingException;

import com.mulesoft.connectors.jev.internal.connection.JevConnection;
import com.mulesoft.connectors.jev.internal.connection.RouteDefaults;
import com.mulesoft.connectors.jev.internal.connection.RouteType;
import com.mulesoft.connectors.jev.internal.provider.ProviderAdapter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Offers model ids for the {@code model} parameter at design time. It starts from the route's static defaults and, when
 * the connected route can enumerate models, merges in a live {@code list-models} call. A failed or slow live call falls
 * back silently to the static list, so design-time editing never blocks on a provider outage.
 */
public class ModelValueProvider implements ValueProvider {

  private static final long LIVE_TIMEOUT_SECONDS = 5L;

  @Connection
  private JevConnection connection;

  @Override
  public Set<Value> resolve() throws ValueResolvingException {
    ProviderAdapter primary = connection.primary();
    Set<String> ids = new LinkedHashSet<>(RouteDefaults.models(RouteType.fromRouteName(primary.routeName())));
    if (primary.capabilities().isSupportsModelList()) {
      try {
        ids.addAll(primary.listModels().get(LIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (Exception ignored) {
        // A live listing failure falls back to the static defaults; design-time editing must not break.
      }
    }
    Set<Value> values = new LinkedHashSet<>();
    for (String id : ids) {
      values.add(ValueBuilder.newValue(id).build());
    }
    return values;
  }

  @Override
  public String getId() {
    return "jev-model-values";
  }
}
