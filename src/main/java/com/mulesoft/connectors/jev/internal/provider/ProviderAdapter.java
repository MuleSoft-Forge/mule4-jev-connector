package com.mulesoft.connectors.jev.internal.provider;

import java.util.concurrent.CompletableFuture;

import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.domain.DecisionResponse;

/**
 * A route-specific view of the Jev decision service. Operations never see a provider directly: they
 * build a canonical request, hand it to the decision engine, and the engine calls one of these.
 */
public interface ProviderAdapter {

  /**
   * @return the route name recorded in {@code attributes.provider}, e.g. {@code typesafe},
   *         {@code openrouter}, {@code cloudflare}, {@code vercel}, {@code compatible}, {@code mock}.
   */
  String routeName();

  /**
   * @return the static capabilities of this route.
   */
  Capabilities capabilities();

  /**
   * Sends the request without blocking. The future completes with a parsed response, or completes
   * exceptionally with a {@code ProviderHttpException} (non-2xx), a transport failure, or a
   * {@code ModuleException} for an unparseable body.
   *
   * @param request the canonical, wire-ready request.
   * @return a future of the parsed response.
   */
  CompletableFuture<DecisionResponse> evaluate(DecisionRequest request);
}
