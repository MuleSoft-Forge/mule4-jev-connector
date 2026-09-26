package com.mulesoft.connectors.jev.internal.operation;

import org.mule.sdk.api.annotation.Alias;
import org.mule.sdk.api.annotation.error.Throws;
import org.mule.sdk.api.annotation.param.Config;
import org.mule.sdk.api.annotation.param.Connection;
import org.mule.sdk.api.annotation.param.Content;
import org.mule.sdk.api.annotation.param.MediaType;
import org.mule.sdk.api.annotation.param.Optional;
import org.mule.sdk.api.annotation.param.ParameterGroup;
import org.mule.sdk.api.annotation.param.display.DisplayName;
import org.mule.sdk.api.annotation.param.display.Summary;
import org.mule.sdk.api.exception.ModuleException;
import org.mule.sdk.api.runtime.operation.Result;
import org.mule.sdk.api.runtime.process.CompletionCallback;

import com.mulesoft.connectors.jev.api.attributes.BatchAttributes;
import com.mulesoft.connectors.jev.api.attributes.TokenUsage;
import com.mulesoft.connectors.jev.internal.cache.DecisionCache;
import com.mulesoft.connectors.jev.internal.config.JevConfiguration;
import com.mulesoft.connectors.jev.internal.connection.JevConnection;
import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.engine.BudgetGuard;
import com.mulesoft.connectors.jev.internal.engine.DecisionContext;
import com.mulesoft.connectors.jev.internal.engine.DecisionOutcome;
import com.mulesoft.connectors.jev.internal.error.BatchErrorTypeProvider;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.stats.DecisionStatsRecorder;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The scale operations. {@code evaluate-batch} runs one question set over many states, fanned out behind a concurrency
 * limit with per-item budgeting, de-duplication and caching; {@code filter} packs a yes/no question about many items
 * into a few calls and keeps the ones that clear a threshold. Both are non-blocking: they compose the engine's
 * {@link CompletableFuture}s through {@link BoundedFanout} and never sleep or block a thread.
 */
public class BatchOperations {

  private static final int MAX_CONCURRENCY = 32;
  private static final int MAX_CHUNK_SIZE = 100;

  /**
   * Evaluates one question set against many states. Items are fanned out with at most {@code maxConcurrency} in flight;
   * identical states are evaluated once when {@code deduplicate} is set; each item is budget-checked, so a limit turns
   * later items into {@code SKIPPED_BUDGET} rather than failing the operation. The payload is an array of per-item
   * results; {@link BatchAttributes} carry the totals. A single failed item never fails the operation unless
   * {@code failFast} is set.
   */
  @Alias("evaluate-batch")
  @DisplayName("[Decide] Evaluate Batch")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @Throws(BatchErrorTypeProvider.class)
  public void evaluateBatch(@Config JevConfiguration config, @Connection JevConnection connection,
      @Content @DisplayName("Items") InputStream items,
      @Optional @Content(primary = false) @DisplayName("Questions") InputStream questions,
      @Optional @DisplayName("Question set") String questionSet, @Optional String questionSetId,
      @Optional String questionSetVersion,
      @Optional @DisplayName("Key field") @Summary("Field on each item copied to the result's 'key'.") String keyField,
      @Optional(defaultValue = "4") @Summary("Maximum items evaluated concurrently (1-32).") int maxConcurrency,
      @Optional(defaultValue = "true") @Summary("Evaluate identical states only once.") boolean deduplicate,
      @Optional(defaultValue = "1000") @Summary("Reject batches larger than this; use a Mule Batch Job "
          + "instead.") int maxItems,
      @Optional(defaultValue = "false") @Summary("Fail the whole operation on the first item error.") boolean failFast,
      @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, BatchAttributes> callback) {

    List<JsonNode> states;
    ObjectNode questionsNode;
    Map<String, String> noMatchOptions;
    String setId;
    String setVersion;
    try {
      states = readArray(items);
      if (states.size() > maxItems) {
        throw new ModuleException("Batch has " + states.size() + " items, over the limit of " + maxItems
            + "; use a Mule Batch Job for larger workloads", JevErrorType.BATCH_TOO_LARGE);
      }
      QuestionResolution.Resolved resolved = QuestionResolution.resolve(config, questions, questionSet, questionSetId,
          questionSetVersion);
      questionsNode = resolved.questions;
      noMatchOptions = resolved.noMatchOptions;
      setId = resolved.questionSetId;
      setVersion = resolved.questionSetVersion;
    } catch (ModuleException e) {
      callback.error(e);
      return;
    } catch (RuntimeException e) {
      callback
          .error(new ModuleException("Could not parse the batch input as JSON", JevErrorType.INVALID_QUESTION_SET, e));
      return;
    }

    if (states.isEmpty()) {
      emitBatch(callback, new ArrayList<>(), new int[0], new ArrayList<>(), states, keyField);
      return;
    }

    // Build one request per item, then map items onto de-duplicated unique requests so each state is billed once.
    List<DecisionRequest> requests = new ArrayList<>(states.size());
    for (JsonNode state : states) {
      requests.add(
          new DecisionRequest(state, options.getModelOverride(), questionsNode, noMatchOptions, setId, setVersion));
    }
    int[] itemToUnique = new int[states.size()];
    List<DecisionRequest> unique = new ArrayList<>();
    Map<String, Integer> byHash = new HashMap<>();
    for (int i = 0; i < requests.size(); i++) {
      Integer uniqueIndex = null;
      if (deduplicate) {
        String hash = Json.sha256(states.get(i));
        uniqueIndex = byHash.get(hash);
        if (uniqueIndex == null) {
          uniqueIndex = unique.size();
          byHash.put(hash, uniqueIndex);
          unique.add(requests.get(i));
        }
      } else {
        uniqueIndex = unique.size();
        unique.add(requests.get(i));
      }
      itemToUnique[i] = uniqueIndex;
    }

    DecisionContext context = new DecisionContext(config.getPricePerMillionInputTokens(),
        options.isIncludeRawResponse(), options.getStep());
    boolean useCache = config.isCacheEnabled() && options.isUseCache() && connection.cache() != null;
    List<Supplier<CompletableFuture<UnitResult>>> tasks = new ArrayList<>(unique.size());
    for (DecisionRequest request : unique) {
      tasks.add(() -> decide(config, connection, request, context, useCache));
    }

    int concurrency = Math.max(1, Math.min(maxConcurrency, MAX_CONCURRENCY));
    List<JsonNode> statesFinal = states;
    int[] mapping = itemToUnique;
    BoundedFanout.run(concurrency, tasks).whenComplete((uniqueResults, error) -> {
      if (error != null) {
        callback.error(unwrap(error));
        return;
      }
      if (failFast) {
        for (UnitResult result : uniqueResults) {
          if (result.status == Status.ERROR) {
            callback.error(result.error);
            return;
          }
        }
      }
      emitBatch(callback, uniqueResults, mapping, unique, statesFinal, keyField);
    });
  }

  /**
   * Keeps the items for which a yes/no question clears a probability threshold. Items are packed several to a call (one
   * Noul question per item, {@code chunkSize} items per chunk) so filtering a long list costs a handful of calls rather
   * than one per item. The payload is {@code {kept, dropped, scores}} — {@code kept}/{@code dropped} are the original
   * items partitioned by the threshold, {@code scores} carries each item's index, {@code noul} (the probability of
   * "yes") and whether it was kept. {@link BatchAttributes} carry the totals.
   */
  @Alias("filter")
  @DisplayName("[Select] Filter")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @Throws(BatchErrorTypeProvider.class)
  public void filter(@Config JevConfiguration config, @Connection JevConnection connection,
      @Content @DisplayName("Items") InputStream items,
      @DisplayName("Question") @Summary("The yes/no question asked of each item.") String question,
      @Optional(defaultValue = "0.5") @Summary("Keep items whose probability of 'yes' is at least this.") double threshold,
      @Optional(defaultValue = "20") @Summary("Items packed into one call (1-100).") int chunkSize,
      @Optional @DisplayName("Text field") @Summary("Field whose text is shown to the model; the whole item when "
          + "unset.") String textField,
      @Optional(defaultValue = "4") @Summary("Maximum chunks evaluated concurrently (1-32).") int maxConcurrency,
      @ParameterGroup(name = "Request options") RequestOptions options,
      CompletionCallback<InputStream, BatchAttributes> callback) {

    List<JsonNode> states;
    try {
      states = readArray(items);
    } catch (ModuleException e) {
      callback.error(e);
      return;
    } catch (RuntimeException e) {
      callback
          .error(new ModuleException("Could not parse the filter input as JSON", JevErrorType.INVALID_QUESTION_SET, e));
      return;
    }

    if (states.isEmpty()) {
      emitFilter(callback, states, new ArrayList<>(), new ArrayList<>(), threshold);
      return;
    }

    int chunk = Math.max(1, Math.min(chunkSize, MAX_CHUNK_SIZE));
    List<int[]> chunks = new ArrayList<>();
    for (int start = 0; start < states.size(); start += chunk) {
      chunks.add(new int[]{start, Math.min(start + chunk, states.size())});
    }

    DecisionContext context = new DecisionContext(config.getPricePerMillionInputTokens(),
        options.isIncludeRawResponse(), options.getStep());
    boolean useCache = config.isCacheEnabled() && options.isUseCache() && connection.cache() != null;
    List<Supplier<CompletableFuture<UnitResult>>> tasks = new ArrayList<>(chunks.size());
    for (int[] bounds : chunks) {
      ObjectNode questions = Json.object();
      for (int i = bounds[0]; i < bounds[1]; i++) {
        ObjectNode noul = questions.putObject("item" + (i - bounds[0]));
        noul.put("type", "noul");
        noul.put("instructions", question + "\n\nItem:\n" + itemText(states.get(i), textField));
      }
      DecisionRequest request = new DecisionRequest(Json.object(), options.getModelOverride(), questions,
          new HashMap<>(), null, null);
      tasks.add(() -> decide(config, connection, request, context, useCache));
    }

    int concurrency = Math.max(1, Math.min(maxConcurrency, MAX_CONCURRENCY));
    List<JsonNode> statesFinal = states;
    List<int[]> chunksFinal = chunks;
    BoundedFanout.run(concurrency, tasks).whenComplete((chunkResults, error) -> {
      if (error != null) {
        callback.error(unwrap(error));
        return;
      }
      emitFilter(callback, statesFinal, chunksFinal, chunkResults, threshold);
    });
  }

  /**
   * Partitions the items by the threshold from the per-chunk Noul answers, then streams {@code {kept,dropped,scores}}.
   */
  private void emitFilter(CompletionCallback<InputStream, BatchAttributes> callback, List<JsonNode> states,
      List<int[]> chunks, List<UnitResult> chunkResults, double threshold) {
    ArrayNode kept = Json.mapper().createArrayNode();
    ArrayNode dropped = Json.mapper().createArrayNode();
    ArrayNode scores = Json.mapper().createArrayNode();
    int inputTokens = 0;
    int outputTokens = 0;
    int cached = 0;
    BigDecimal cost = null;

    for (int c = 0; c < chunks.size(); c++) {
      int[] bounds = chunks.get(c);
      UnitResult result = chunkResults.get(c);
      if (result.status == Status.ERROR) {
        callback.error(result.error);
        return;
      }
      if (result.status == Status.SKIPPED_BUDGET) {
        callback.error(
            new ModuleException("Budget limit reached before the filter completed", JevErrorType.BUDGET_EXCEEDED));
        return;
      }
      if (result.cached) {
        cached += bounds[1] - bounds[0];
      } else {
        TokenUsage usage = result.outcome.attributes().getUsage();
        if (usage != null) {
          inputTokens += usage.getInputTokens() == null ? 0 : usage.getInputTokens();
          outputTokens += usage.getOutputTokens() == null ? 0 : usage.getOutputTokens();
        }
        BigDecimal chunkCost = result.outcome.attributes().getEstimatedCostUsd();
        if (chunkCost != null) {
          cost = cost == null ? chunkCost : cost.add(chunkCost);
        }
      }

      ObjectNode answers = result.outcome.payload();
      for (int i = bounds[0]; i < bounds[1]; i++) {
        JsonNode answer = answers.path("item" + (i - bounds[0]));
        double noul = answer.path("noul").asDouble(0.0);
        boolean keep = noul >= threshold;
        scores.addObject().put("index", i).put("noul", noul).put("kept", keep);
        if (keep) {
          kept.add(states.get(i).deepCopy());
        } else {
          dropped.add(states.get(i).deepCopy());
        }
      }
    }

    ObjectNode payload = Json.object();
    payload.set("kept", kept);
    payload.set("dropped", dropped);
    payload.set("scores", scores);
    BatchAttributes attributes = new BatchAttributes(states.size(), kept.size(), 0, 0, cached,
        new TokenUsage(inputTokens, outputTokens), cost);
    byte[] bytes = Json.write(payload).getBytes(StandardCharsets.UTF_8);
    callback.success(Result.<InputStream, BatchAttributes>builder().output(new ByteArrayInputStream(bytes))
        .attributes(attributes).build());
  }

  private static String itemText(JsonNode item, String textField) {
    if (textField != null && !textField.isBlank()) {
      JsonNode value = item.get(textField);
      return value != null && !value.isNull() ? value.asText() : "";
    }
    return Json.write(item);
  }

  /** Runs one decision through the cache → budget → engine path, capturing every outcome as a total result. */
  private CompletableFuture<UnitResult> decide(JevConfiguration config, JevConnection connection,
      DecisionRequest request, DecisionContext context, boolean useCache) {
    DecisionCache cache = connection.cache();
    String cacheKey = null;
    if (useCache) {
      cacheKey = cache.keyFor(connection.primary().routeName(), request.requestedModel(), request);
      java.util.Optional<DecisionOutcome> hit = cache.lookup(cacheKey, config.cacheTtlMillis());
      if (hit.isPresent()) {
        return CompletableFuture.completedFuture(UnitResult.ok(hit.get(), true));
      }
    }

    BudgetGuard budget = connection.budget();
    if (budget != null && config.isBudgetEnabled()) {
      try {
        budget.reserve(config.getBudgetMaxCallsPerWindow(), config.getBudgetMaxInputTokensPerWindow(),
            config.budgetWindowMillis());
      } catch (ModuleException e) {
        if (e.getType() == JevErrorType.BUDGET_EXCEEDED) {
          return CompletableFuture.completedFuture(UnitResult.skippedBudget());
        }
        return CompletableFuture.completedFuture(UnitResult.error(e));
      }
    }

    final String key = cacheKey;
    final boolean cacheThis = useCache;
    return connection.engine().evaluate(connection, request, context).handle((outcome, error) -> {
      if (error != null) {
        return UnitResult.error(unwrap(error));
      }
      if (budget != null && config.isBudgetEnabled()) {
        Integer inputTokens = outcome.attributes().getUsage() == null
            ? null
            : outcome.attributes().getUsage().getInputTokens();
        budget.recordUsage(inputTokens == null ? null : inputTokens.longValue(), config.budgetWindowMillis());
      }
      if (cacheThis && cache != null) {
        cache.put(key, outcome);
      }
      if (connection.stats() != null && config.isStatsEnabled()) {
        connection.stats().record(request, outcome, DecisionStatsRecorder.DEFAULT_WINDOW_SIZE);
      }
      return UnitResult.ok(outcome, false);
    });
  }

  /** Builds the per-item payload array and the summed {@link BatchAttributes}, then streams the success result. */
  private void emitBatch(CompletionCallback<InputStream, BatchAttributes> callback, List<UnitResult> uniqueResults,
      int[] itemToUnique, List<DecisionRequest> unique, List<JsonNode> states, String keyField) {
    ArrayNode payload = Json.mapper().createArrayNode();
    int succeeded = 0;
    int failed = 0;
    int skippedBudget = 0;
    int cached = 0;
    for (int i = 0; i < states.size(); i++) {
      UnitResult result = uniqueResults.get(itemToUnique[i]);
      ObjectNode item = payload.addObject();
      item.put("index", i);
      String key = keyValue(states.get(i), keyField);
      if (key == null) {
        item.putNull("key");
      } else {
        item.put("key", key);
      }
      switch (result.status) {
        case OK :
          item.put("status", "OK");
          item.put("cached", result.cached);
          item.set("answers", result.outcome.payload());
          succeeded++;
          if (result.cached) {
            cached++;
          }
          break;
        case SKIPPED_BUDGET :
          item.put("status", "SKIPPED_BUDGET");
          skippedBudget++;
          break;
        default :
          item.put("status", "ERROR");
          ObjectNode err = item.putObject("error");
          err.put("type", errorType(result.error));
          err.put("message", result.error.getMessage());
          failed++;
          break;
      }
    }

    // Usage and cost are billed once per unique decision, not per item, so a de-duplicated batch reports honest spend.
    int inputTokens = 0;
    int outputTokens = 0;
    BigDecimal cost = null;
    for (UnitResult result : uniqueResults) {
      if (result.status != Status.OK || result.cached) {
        continue;
      }
      TokenUsage usage = result.outcome.attributes().getUsage();
      if (usage != null) {
        inputTokens += usage.getInputTokens() == null ? 0 : usage.getInputTokens();
        outputTokens += usage.getOutputTokens() == null ? 0 : usage.getOutputTokens();
      }
      BigDecimal itemCost = result.outcome.attributes().getEstimatedCostUsd();
      if (itemCost != null) {
        cost = cost == null ? itemCost : cost.add(itemCost);
      }
    }

    BatchAttributes attributes = new BatchAttributes(states.size(), succeeded, failed, skippedBudget, cached,
        new TokenUsage(inputTokens, outputTokens), cost);
    byte[] bytes = Json.write(payload).getBytes(StandardCharsets.UTF_8);
    callback.success(Result.<InputStream, BatchAttributes>builder().output(new ByteArrayInputStream(bytes))
        .attributes(attributes).build());
  }

  private static String keyValue(JsonNode state, String keyField) {
    if (keyField == null || keyField.isBlank()) {
      return null;
    }
    JsonNode value = state.get(keyField);
    return value != null && !value.isNull() ? value.asText() : null;
  }

  private static List<JsonNode> readArray(InputStream in) {
    JsonNode node = Json.read(in);
    if (node == null || !node.isArray()) {
      throw new ModuleException("items must be a JSON array", JevErrorType.INVALID_QUESTION_SET);
    }
    List<JsonNode> list = new ArrayList<>();
    node.forEach(list::add);
    return list;
  }

  private static String errorType(ModuleException error) {
    Object type = error.getType();
    if (type instanceof Enum) {
      return ((Enum<?>) type).name();
    }
    return type == null ? "UNKNOWN" : type.toString();
  }

  private static ModuleException unwrap(Throwable error) {
    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    if (cause instanceof ModuleException) {
      return (ModuleException) cause;
    }
    return new ModuleException("Decision failed: " + cause.getMessage(), JevErrorType.INVALID_RESPONSE, cause);
  }

  private enum Status {
    OK, ERROR, SKIPPED_BUDGET
  }

  /** A total per-item result: never completes exceptionally, so the fan-out can settle every unit. */
  private static final class UnitResult {

    private final Status status;
    private final DecisionOutcome outcome;
    private final boolean cached;
    private final ModuleException error;

    private UnitResult(Status status, DecisionOutcome outcome, boolean cached, ModuleException error) {
      this.status = status;
      this.outcome = outcome;
      this.cached = cached;
      this.error = error;
    }

    static UnitResult ok(DecisionOutcome outcome, boolean cached) {
      return new UnitResult(Status.OK, outcome, cached, null);
    }

    static UnitResult skippedBudget() {
      return new UnitResult(Status.SKIPPED_BUDGET, null, false, null);
    }

    static UnitResult error(ModuleException error) {
      return new UnitResult(Status.ERROR, null, false, error);
    }
  }
}
