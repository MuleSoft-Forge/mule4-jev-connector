package com.mulesoft.connectors.jev.internal.operation;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerService;
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

import com.mulesoft.connectors.jev.api.attributes.DecisionAttributes;
import com.mulesoft.connectors.jev.internal.config.JevConfiguration;
import com.mulesoft.connectors.jev.internal.connection.JevConnection;
import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.engine.DecisionContext;
import com.mulesoft.connectors.jev.internal.engine.DecisionEngine;
import com.mulesoft.connectors.jev.internal.engine.DelayScheduler;
import com.mulesoft.connectors.jev.internal.engine.RetryPolicy;
import com.mulesoft.connectors.jev.internal.error.DecisionErrorTypeProvider;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;

/**
 * The billed decision operations. {@code evaluate} sends one decision without blocking: it parses the state and
 * questions, hands a canonical request to the {@link DecisionEngine}, and streams the enriched answers back as a JSON
 * payload with {@link DecisionAttributes}. Retries run on a runtime scheduler, so no I/O thread ever sleeps.
 */
public class DecisionOperations
    implements
      org.mule.runtime.api.lifecycle.Startable,
      org.mule.runtime.api.lifecycle.Stoppable {

  private static final String NO_MATCH_OPTION = "noMatchOption";

  @Inject
  private SchedulerService schedulerService;

  private Scheduler scheduler;
  private DecisionEngine engine;

  @Override
  public void start() {
    scheduler = schedulerService.cpuLightScheduler();
    engine = new DecisionEngine(new RetryPolicy(), DelayScheduler.on(scheduler));
  }

  @Override
  public void stop() {
    if (scheduler != null) {
      scheduler.stop();
    }
  }

  /**
   * Evaluates one decision against the connected route. The output is the answers object (each answer enriched with a
   * {@code derived} block); {@code attributes} carry provider, usage, cost and timing.
   */
  @Alias("evaluate")
  @DisplayName("Evaluate")
  @MediaType(value = MediaType.APPLICATION_JSON, strict = false)
  @Throws(DecisionErrorTypeProvider.class)
  public void evaluate(@Config JevConfiguration config, @Connection JevConnection connection,
      @Content InputStream state, @Content(primary = false) @DisplayName("Questions") InputStream questions,
      @Optional String model, @Optional String questionSetId, @Optional String questionSetVersion,
      @Optional(defaultValue = "false") boolean includeRawResponse,
      CompletionCallback<InputStream, DecisionAttributes> callback) {
    DecisionRequest request;
    try {
      request = buildRequest(state, questions, model, questionSetId, questionSetVersion);
    } catch (ModuleException e) {
      callback.error(e);
      return;
    } catch (RuntimeException e) {
      callback.error(
          new ModuleException("Could not parse the decision input as JSON", JevErrorType.INVALID_QUESTION_SET, e));
      return;
    }

    DecisionContext context = new DecisionContext(config.getPricePerMillionInputTokens(), includeRawResponse);
    engine.evaluate(connection, request, context).whenComplete((outcome, error) -> {
      if (error != null) {
        callback.error(unwrap(error));
        return;
      }
      byte[] payload = Json.write(outcome.payload()).getBytes(StandardCharsets.UTF_8);
      callback.success(Result.<InputStream, DecisionAttributes>builder().output(new ByteArrayInputStream(payload))
          .attributes(outcome.attributes()).build());
    });
  }

  private static DecisionRequest buildRequest(InputStream state, InputStream questions, String model,
      String questionSetId, String questionSetVersion) {
    JsonNode stateNode = Json.read(state);
    JsonNode questionsNode = Json.read(questions);
    if (questionsNode == null || !questionsNode.isObject()) {
      throw new ModuleException("The questions input must be a JSON object keyed by question id",
          JevErrorType.INVALID_QUESTION_SET);
    }

    ObjectNode cleaned = Json.object();
    Map<String, String> noMatchOptions = new HashMap<>();
    Iterator<Map.Entry<String, JsonNode>> it = questionsNode.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> entry = it.next();
      JsonNode question = entry.getValue();
      if (!question.isObject()) {
        cleaned.set(entry.getKey(), question);
        continue;
      }
      ObjectNode copy = (ObjectNode) question.deepCopy();
      JsonNode noMatch = copy.remove(NO_MATCH_OPTION);
      if (noMatch != null && noMatch.isTextual()) {
        noMatchOptions.put(entry.getKey(), noMatch.asText());
      }
      cleaned.set(entry.getKey(), copy);
    }

    return new DecisionRequest(stateNode, model, cleaned, noMatchOptions, questionSetId, questionSetVersion);
  }

  private static Throwable unwrap(Throwable error) {
    if (error instanceof CompletionException && error.getCause() != null) {
      return error.getCause();
    }
    return error;
  }
}
