package com.mulesoft.connectors.jev.internal.engine;

import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.jev.api.attributes.DecisionAttributes;
import com.mulesoft.connectors.jev.api.attributes.TokenUsage;
import com.mulesoft.connectors.jev.api.attributes.TraceEntry;
import com.mulesoft.connectors.jev.internal.connection.JevConnection;
import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.domain.DecisionResponse;
import com.mulesoft.connectors.jev.internal.domain.DerivedComputer;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.http.HttpErrorMapper;
import com.mulesoft.connectors.jev.internal.http.ProviderHttpException;
import com.mulesoft.connectors.jev.internal.provider.ProviderAdapter;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Drives a single decision: it calls the adapter without blocking, retries transient failures on a scheduler (never a
 * sleeping I/O thread), then enriches the answers, resolves cost and assembles the out-of-band attributes. When the
 * primary route exhausts its retries with a connectivity-class error, the engine fails over to the next configured
 * fallback, in order, recording the abandoned routes in {@code attributes.failedOverFrom}. Validation and authorization
 * failures are terminal and never trigger failover.
 */
public final class DecisionEngine {

  private static final BigDecimal MILLION = new BigDecimal(1_000_000);

  private final RetryPolicy retryPolicy;
  private final DelayScheduler delayScheduler;

  public DecisionEngine(RetryPolicy retryPolicy, DelayScheduler delayScheduler) {
    this.retryPolicy = retryPolicy;
    this.delayScheduler = delayScheduler;
  }

  /** Evaluates the request against the primary route, failing over to the fallbacks on connectivity-class errors. */
  public CompletableFuture<DecisionOutcome> evaluate(JevConnection connection, DecisionRequest request,
      DecisionContext context) {
    List<ProviderAdapter> route = new ArrayList<>();
    route.add(connection.primary());
    route.addAll(connection.fallbacks());
    return runRoute(route, 0, request, context, System.nanoTime(), new ArrayList<>());
  }

  private CompletableFuture<DecisionOutcome> runRoute(List<ProviderAdapter> route, int index, DecisionRequest request,
      DecisionContext context, long startNanos, List<String> failedOverFrom) {
    ProviderAdapter adapter = route.get(index);
    AtomicInteger attempts = new AtomicInteger(0);
    return attempt(adapter, request, 1, attempts).thenApply(response -> {
      long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
      return assemble(adapter, request, context, response, attempts.get(), latencyMs, failedOverFrom);
    }).exceptionallyCompose(error -> {
      Throwable cause = unwrap(error);
      if (index + 1 < route.size() && isFailoverable(cause)) {
        List<String> abandoned = new ArrayList<>(failedOverFrom);
        abandoned.add(adapter.routeName());
        return runRoute(route, index + 1, request, context, startNanos, abandoned);
      }
      return CompletableFuture.failedFuture(cause);
    });
  }

  /** Only connectivity-class terminal errors fail over; validation and authorization errors are raised as-is. */
  private static boolean isFailoverable(Throwable cause) {
    if (!(cause instanceof ModuleException)) {
      return false;
    }
    Object type = ((ModuleException) cause).getType();
    return type == JevErrorType.CONNECTIVITY || type == JevErrorType.RATE_LIMITED || type == JevErrorType.OVERLOADED
        || type == JevErrorType.TIMEOUT;
  }

  private CompletableFuture<DecisionResponse> attempt(ProviderAdapter adapter, DecisionRequest request, int attemptNo,
      AtomicInteger attempts) {
    attempts.set(attemptNo);
    CompletableFuture<DecisionResponse> future;
    try {
      future = adapter.evaluate(request);
    } catch (RuntimeException e) {
      future = CompletableFuture.failedFuture(e);
    }
    return future.exceptionallyCompose(error -> onError(adapter, request, attemptNo, attempts, error));
  }

  private CompletableFuture<DecisionResponse> onError(ProviderAdapter adapter, DecisionRequest request, int attemptNo,
      AtomicInteger attempts, Throwable error) {
    Throwable cause = unwrap(error);

    // A body that could not be parsed is already a terminal, typed error; do not retry it.
    if (cause instanceof ModuleException) {
      return CompletableFuture.failedFuture(cause);
    }

    boolean retriable;
    OptionalLong serverDelay = OptionalLong.empty();
    if (cause instanceof ProviderHttpException) {
      ProviderHttpException httpError = (ProviderHttpException) cause;
      retriable = retryPolicy.shouldRetry(httpError.status());
      serverDelay = httpError.retryAfterMs();
    } else {
      // Transport failure (I/O, DNS, TLS, timeout): connectivity class, retriable.
      retriable = true;
    }

    boolean retriesLeft = (attemptNo - 1) < retryPolicy.maxRetries();
    if (retriable && retriesLeft) {
      long delayMs = retryPolicy.delayMs(attemptNo, serverDelay);
      return delayScheduler.after(delayMs).thenCompose(v -> attempt(adapter, request, attemptNo + 1, attempts));
    }
    return CompletableFuture.failedFuture(toTerminal(cause));
  }

  private static Throwable toTerminal(Throwable cause) {
    if (cause instanceof ModuleException) {
      return cause;
    }
    if (cause instanceof ProviderHttpException) {
      ProviderHttpException httpError = (ProviderHttpException) cause;
      return HttpErrorMapper.toException(httpError.status(), httpError.body());
    }
    if (cause instanceof TimeoutException) {
      return new ModuleException("Request timed out", JevErrorType.TIMEOUT, cause);
    }
    return new ModuleException("Could not reach the provider: " + cause.getMessage(), JevErrorType.CONNECTIVITY, cause);
  }

  private DecisionOutcome assemble(ProviderAdapter adapter, DecisionRequest request, DecisionContext context,
      DecisionResponse response, int attempts, long latencyMs, List<String> failedOverFrom) {
    ObjectNode answers = response.answers();
    DerivedComputer.enrich(answers, request.noMatchOptions());

    BigDecimal cost;
    String costSource;
    if (response.providerReportedCost() != null) {
      cost = response.providerReportedCost();
      costSource = "PROVIDER";
    } else if (response.inputTokens() != null && context.pricePerMillionInputTokens() != null) {
      cost = context.pricePerMillionInputTokens().multiply(new BigDecimal(response.inputTokens())).divide(MILLION,
          MathContext.DECIMAL64);
      costSource = "ESTIMATE";
    } else {
      cost = null;
      costSource = "NONE";
    }

    TraceEntry traceEntry = TraceEntry.builder().step(context.step()).provider(adapter.routeName())
        .model(response.model()).latencyMs(latencyMs).attempts(attempts).estimatedCostUsd(cost)
        .questionSetId(request.questionSetId()).failedOverFrom(failedOverFrom).build();

    DecisionAttributes attributes = DecisionAttributes.builder().provider(adapter.routeName())
        .requestedModel(response.requestedModel()).model(response.model())
        .usage(new TokenUsage(response.inputTokens(), response.outputTokens())).estimatedCostUsd(cost)
        .costSource(costSource).latencyMs(latencyMs).attempts(attempts).failedOverFrom(failedOverFrom).cacheHit(false)
        .questionSetId(request.questionSetId()).questionSetVersion(request.questionSetVersion())
        .stateHash(request.state() == null ? null : Json.sha256(request.state()))
        .providerRequestId(response.providerRequestId())
        .rawResponse(context.includeRawResponse() ? response.rawBody() : null).traceEntry(traceEntry).build();

    return new DecisionOutcome(answers, attributes);
  }

  private static Throwable unwrap(Throwable error) {
    if (error instanceof CompletionException && error.getCause() != null) {
      return error.getCause();
    }
    return error;
  }
}
