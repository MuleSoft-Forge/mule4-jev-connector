package com.mulesoft.connectors.jev.internal.operation;

import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.error.Throws;
import org.mule.sdk.api.annotation.param.Config;
import org.mule.sdk.api.annotation.param.Connection;
import org.mule.sdk.api.annotation.param.Content;
import org.mule.sdk.api.annotation.param.MediaType;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.exception.ModuleException;
import org.mule.sdk.api.runtime.operation.Result;
import org.mule.sdk.api.runtime.process.CompletionCallback;

import com.mulesoft.connectors.jev.internal.config.JevConfiguration;
import com.mulesoft.connectors.jev.internal.connection.JevConnection;
import com.mulesoft.connectors.jev.internal.error.DecisionErrorTypeProvider;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.http.HttpErrorMapper;
import com.mulesoft.connectors.jev.internal.http.ProviderHttpException;
import com.mulesoft.connectors.jev.internal.provider.ProviderAdapter;
import com.mulesoft.connectors.jev.internal.provider.RouteCapabilities;
import com.mulesoft.connectors.jev.internal.questionset.QuestionSet;
import com.mulesoft.connectors.jev.internal.questionset.QuestionSetLoader;
import com.mulesoft.connectors.jev.internal.util.Json;
import com.mulesoft.connectors.jev.internal.validation.QuestionSetValidator;
import com.mulesoft.connectors.jev.internal.validation.ValidationResult;
import com.mulesoft.connectors.jev.internal.value.QuestionSetValueProvider;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Local utility operations. {@code get-capabilities} answers purely from connection metadata; {@code list-models}
 * performs a non-blocking model-list call on every connected route that supports one.
 */
public class UtilityOperations {

  /**
   * Returns the capabilities of every connected route — the primary first, then each fallback — so a flow can see what
   * Noul/Choice/Score support, confidence, model listing and option/level ceilings each route offers. Makes no provider
   * call.
   *
   * @param connection
   *          the resolved Jev connection.
   * @return the per-route capabilities.
   */
  @Alias("get-capabilities")
  @DisplayName("[Util] Get Capabilities")
  public List<RouteCapabilities> getCapabilities(@Connection JevConnection connection) {
    List<RouteCapabilities> capabilities = new ArrayList<>();
    ProviderAdapter primary = connection.primary();
    capabilities.add(new RouteCapabilities(primary.routeName(), true, primary.capabilities()));
    for (ProviderAdapter fallback : connection.fallbacks()) {
      capabilities.add(new RouteCapabilities(fallback.routeName(), false, fallback.capabilities()));
    }
    return capabilities;
  }

  /**
   * Lists the models available on the connected routes as a JSON array of {@code {id, route}} entries, primary route
   * first. Routes that cannot enumerate models are skipped; if no connected route supports model listing, the operation
   * raises {@code JEV:UNSUPPORTED_BY_PROVIDER}.
   */
  @Alias("list-models")
  @DisplayName("[Util] List Models")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @Throws(DecisionErrorTypeProvider.class)
  public void listModels(@Connection JevConnection connection, CompletionCallback<InputStream, Void> callback) {
    List<ProviderAdapter> adapters = new ArrayList<>();
    adapters.add(connection.primary());
    adapters.addAll(connection.fallbacks());

    List<ProviderAdapter> supporting = new ArrayList<>();
    for (ProviderAdapter adapter : adapters) {
      if (adapter.capabilities().isSupportsModelList()) {
        supporting.add(adapter);
      }
    }
    if (supporting.isEmpty()) {
      callback
          .error(new ModuleException("No connected route can enumerate models", JevErrorType.UNSUPPORTED_BY_PROVIDER));
      return;
    }

    List<CompletableFuture<RouteModels>> futures = new ArrayList<>();
    for (ProviderAdapter adapter : supporting) {
      futures.add(adapter.listModels().thenApply(ids -> new RouteModels(adapter.routeName(), ids)));
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((ignored, error) -> {
      if (error != null) {
        callback.error(toTerminal(unwrap(error)));
        return;
      }
      ArrayNode array = Json.mapper().createArrayNode();
      for (CompletableFuture<RouteModels> future : futures) {
        RouteModels routeModels = future.join();
        for (String id : routeModels.ids()) {
          array.addObject().put("id", id).put("route", routeModels.route());
        }
      }
      byte[] payload = Json.write(array).getBytes(StandardCharsets.UTF_8);
      callback.success(Result.<InputStream, Void>builder().output(new ByteArrayInputStream(payload)).build());
    });
  }

  /**
   * Validates a question set locally, before any billed call, and returns {@code {valid, errors[], warnings[]}}. Errors
   * are the hard API limits (§3); warnings flag legal-but-risky sets. Supply the questions inline ({@code questions})
   * or by naming a classpath file ({@code questionSet}); exactly one is required. Makes no provider call.
   */
  @Alias("validate-question-set")
  @DisplayName("[Util] Validate Question Set")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @Throws(DecisionErrorTypeProvider.class)
  public InputStream validateQuestionSet(@Config JevConfiguration config, @Optional @Content InputStream questions,
      @Optional @DisplayName("Question set") @org.mule.sdk.api.annotation.values.OfValues(QuestionSetValueProvider.class) String questionSet) {
    JsonNode questionsNode = resolveQuestions(config, questions, questionSet);
    Map<String, String> noMatchOptions = noMatchOptions(questionsNode);

    ValidationResult result = new QuestionSetValidator().validate(questionsNode, noMatchOptions);

    ObjectNode payload = Json.object();
    payload.put("valid", result.isValid());
    ArrayNode errors = payload.putArray("errors");
    result.getErrors().forEach(errors::add);
    ArrayNode warnings = payload.putArray("warnings");
    result.getWarnings().forEach(warnings::add);
    return new ByteArrayInputStream(Json.write(payload).getBytes(StandardCharsets.UTF_8));
  }

  private static JsonNode resolveQuestions(JevConfiguration config, InputStream questions, String questionSet) {
    boolean hasInline = questions != null;
    boolean hasFile = questionSet != null && !questionSet.isBlank();
    if (hasInline == hasFile) {
      throw new ModuleException("Supply exactly one of 'questions' or 'questionSet'",
          JevErrorType.INVALID_QUESTION_SET);
    }
    if (hasFile) {
      QuestionSet set = QuestionSetLoader.load(config.getDefaultQuestionSetsLocation(), questionSet);
      return set.questions();
    }
    try {
      return Json.read(questions);
    } catch (RuntimeException e) {
      throw new ModuleException("Could not parse questions as JSON", JevErrorType.INVALID_QUESTION_SET, e);
    }
  }

  /**
   * Collects each question's declared {@code noMatchOption}, so the "missing no-match" warning is not raised for it.
   */
  private static Map<String, String> noMatchOptions(JsonNode questions) {
    Map<String, String> noMatch = new HashMap<>();
    if (questions == null || !questions.isObject()) {
      return noMatch;
    }
    Iterator<Map.Entry<String, JsonNode>> it = questions.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> entry = it.next();
      JsonNode value = entry.getValue();
      if (value.isObject() && value.hasNonNull("noMatchOption")) {
        noMatch.put(entry.getKey(), value.get("noMatchOption").asText());
      }
    }
    return noMatch;
  }

  private static Throwable toTerminal(Throwable cause) {
    if (cause instanceof ModuleException) {
      return cause;
    }
    if (cause instanceof ProviderHttpException) {
      ProviderHttpException httpError = (ProviderHttpException) cause;
      return HttpErrorMapper.toException(httpError.status(), httpError.body());
    }
    return new ModuleException("Could not list models: " + cause.getMessage(), JevErrorType.CONNECTIVITY, cause);
  }

  private static Throwable unwrap(Throwable error) {
    if (error instanceof CompletionException && error.getCause() != null) {
      return error.getCause();
    }
    return error;
  }

  /** A route's model ids, carried through the async merge. */
  private static final class RouteModels {

    private final String route;
    private final List<String> ids;

    RouteModels(String route, List<String> ids) {
      this.route = route;
      this.ids = ids;
    }

    String route() {
      return route;
    }

    List<String> ids() {
      return ids;
    }
  }
}
