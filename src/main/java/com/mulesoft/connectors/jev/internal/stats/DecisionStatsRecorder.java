package com.mulesoft.connectors.jev.internal.stats;

import org.mule.runtime.api.store.ObjectStore;
import org.mule.runtime.api.store.ObjectStoreException;
import org.mule.runtime.api.store.ObjectStoreManager;
import org.mule.runtime.api.store.ObjectStoreSettings;

import com.mulesoft.connectors.jev.api.attributes.DecisionAttributes;
import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.engine.DecisionOutcome;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes compact, privacy-safe counters after every decision so the drift, budget and failover sources can poll them
 * without touching operation threads. It records only aggregates — decision counts, no-match counts, confidence sums
 * and choice/level distributions per {@code questionSetId} — and a bounded ring of recent failover events. It never
 * stores state text, keys or raw bodies.
 *
 * <p>
 * Decisions are aggregated into fixed-size windows of {@code windowSize} decisions. When a window fills it rolls into
 * the {@code previous} slot and a fresh {@code current} window begins; the very first completed window is preserved as
 * {@code first} so a {@code FIRST_WINDOW} baseline is always available. The stats Object Store is persistent and
 * distributed, so counters and the source armed/fired state survive a restart and a limit fires once cluster-wide.
 */
public final class DecisionStatsRecorder {

  /** Decisions per aggregation window; the drift source's {@code windowSize} defaults to this so windows align. */
  public static final int DEFAULT_WINDOW_SIZE = 500;

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionStatsRecorder.class);
  private static final String STORE_NAME = "_jevStats";
  private static final String STATS_PREFIX = "stats:";
  private static final String FAILOVER_KEY = "failover:events";
  private static final String INLINE_SET = "__inline__";
  private static final int MAX_FAILOVER_EVENTS = 500;

  private final ObjectStore<Serializable> store;
  private final Object lock = new Object();

  public DecisionStatsRecorder(ObjectStore<Serializable> store) {
    this.store = store;
  }

  /** Builds a shared, distributed, persistent stats store from the runtime's Object Store manager. */
  public static DecisionStatsRecorder create(ObjectStoreManager manager) {
    ObjectStore<Serializable> store = manager.getOrCreateObjectStore(STORE_NAME,
        ObjectStoreSettings.builder().persistent(true).build());
    return new DecisionStatsRecorder(store);
  }

  /** The raw stats Object Store, for sources that persist their own armed/fired watermark alongside the counters. */
  public ObjectStore<Serializable> store() {
    return store;
  }

  /**
   * Folds one completed decision into the window aggregates for its question set and, when the decision failed over,
   * appends a failover event. A {@code windowSize} of decisions rolls the window.
   */
  public void record(DecisionRequest request, DecisionOutcome outcome, int windowSize) {
    synchronized (lock) {
      try {
        recordDecision(request, outcome, Math.max(1, windowSize));
        recordFailover(outcome.attributes());
      } catch (ObjectStoreException | RuntimeException e) {
        // Monitoring must never fail a decision.
        LOGGER.debug("Stats recording failed; counters may be incomplete", e);
      }
    }
  }

  private void recordDecision(DecisionRequest request, DecisionOutcome outcome, int windowSize)
      throws ObjectStoreException {
    String setId = request.questionSetId() == null ? INLINE_SET : request.questionSetId();
    String key = STATS_PREFIX + setId;
    SetStats stats = load(key, SetStats.class, SetStats::new);

    stats.current.count++;
    ObjectNode answers = outcome.payload();
    Iterator<Map.Entry<String, JsonNode>> it = answers.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> entry = it.next();
      JsonNode answer = entry.getValue();
      if (!answer.isObject()) {
        continue;
      }
      accumulate(stats.current, entry.getKey(), answer);
    }

    if (stats.current.count >= windowSize) {
      if (stats.first == null) {
        stats.first = stats.current.copy();
      }
      stats.previous = stats.current;
      stats.current = new Window();
    }
    persist(key, stats);
  }

  private static void accumulate(Window window, String questionId, JsonNode answer) {
    JsonNode derived = answer.get("derived");
    if (derived != null && derived.path("isNoMatch").asBoolean(false)) {
      window.noMatchCount++;
    }
    if (answer.hasNonNull("confidence")) {
      window.confidenceSum += answer.get("confidence").asDouble();
      window.confidenceCount++;
    }
    String value = null;
    if (answer.hasNonNull("choice")) {
      value = answer.get("choice").asText();
    } else if (answer.hasNonNull("score")) {
      value = answer.get("score").asText();
    }
    if (value != null) {
      window.distribution.computeIfAbsent(questionId, k -> new java.util.HashMap<>()).merge(value, 1L, Long::sum);
    }
  }

  private void recordFailover(DecisionAttributes attributes) throws ObjectStoreException {
    List<String> failedOver = attributes.getFailedOverFrom();
    if (failedOver == null || failedOver.isEmpty()) {
      return;
    }
    FailoverLog log = load(FAILOVER_KEY, FailoverLog.class, FailoverLog::new);
    String from = failedOver.get(failedOver.size() - 1);
    log.events.add(new FailoverEvent(from, attributes.getProvider(), System.currentTimeMillis()));
    while (log.events.size() > MAX_FAILOVER_EVENTS) {
      log.events.remove(0);
    }
    persist(FAILOVER_KEY, log);
  }

  /** Window aggregates for a question set, or {@code null} when nothing has been recorded for it yet. */
  public SetStats windowsFor(String questionSetId) {
    synchronized (lock) {
      String key = STATS_PREFIX + (questionSetId == null ? INLINE_SET : questionSetId);
      try {
        return store.contains(key) ? (SetStats) store.retrieve(key) : null;
      } catch (ObjectStoreException | RuntimeException e) {
        LOGGER.debug("Stats read failed for {}", key, e);
        return null;
      }
    }
  }

  /** Failover events recorded strictly after {@code sinceEpochMs}, oldest first. */
  public List<FailoverEvent> failoverEventsSince(long sinceEpochMs) {
    synchronized (lock) {
      try {
        if (!store.contains(FAILOVER_KEY)) {
          return List.of();
        }
        FailoverLog log = (FailoverLog) store.retrieve(FAILOVER_KEY);
        List<FailoverEvent> recent = new ArrayList<>();
        for (FailoverEvent event : log.events) {
          if (event.timestamp > sinceEpochMs) {
            recent.add(event);
          }
        }
        return recent;
      } catch (ObjectStoreException | RuntimeException e) {
        LOGGER.debug("Failover event read failed", e);
        return List.of();
      }
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends Serializable> T load(String key, Class<T> type, java.util.function.Supplier<T> fresh)
      throws ObjectStoreException {
    if (store.contains(key)) {
      Serializable stored = store.retrieve(key);
      if (type.isInstance(stored)) {
        return (T) stored;
      }
    }
    return fresh.get();
  }

  private void persist(String key, Serializable value) throws ObjectStoreException {
    if (store.contains(key)) {
      store.remove(key);
    }
    store.store(key, value);
  }

  /** Per-question-set window aggregates: the baseline first window, the last full window and the filling one. */
  public static final class SetStats implements Serializable {

    private static final long serialVersionUID = 1L;

    private Window first;
    private Window previous;
    private Window current = new Window();

    public Window first() {
      return first;
    }

    public Window previous() {
      return previous;
    }

    public Window current() {
      return current;
    }
  }

  /** One window of aggregated decision counters. All fields are counts or sums — never state text. */
  public static final class Window implements Serializable {

    private static final long serialVersionUID = 1L;

    private long count;
    private long noMatchCount;
    private double confidenceSum;
    private long confidenceCount;
    private final Map<String, Map<String, Long>> distribution = new java.util.HashMap<>();

    public long count() {
      return count;
    }

    public double noMatchRate() {
      return count == 0 ? 0.0 : (double) noMatchCount / count;
    }

    public double meanConfidence() {
      return confidenceCount == 0 ? 0.0 : confidenceSum / confidenceCount;
    }

    /** Choice/score value counts per question id. */
    public Map<String, Map<String, Long>> distribution() {
      return distribution;
    }

    Window copy() {
      Window copy = new Window();
      copy.count = count;
      copy.noMatchCount = noMatchCount;
      copy.confidenceSum = confidenceSum;
      copy.confidenceCount = confidenceCount;
      for (Map.Entry<String, Map<String, Long>> entry : distribution.entrySet()) {
        copy.distribution.put(entry.getKey(), new java.util.HashMap<>(entry.getValue()));
      }
      return copy;
    }
  }

  /** A recorded failover: the abandoned route, the route that served the request, and when. */
  public static final class FailoverEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String from;
    private final String to;
    private final long timestamp;

    FailoverEvent(String from, String to, long timestamp) {
      this.from = from;
      this.to = to;
      this.timestamp = timestamp;
    }

    public String from() {
      return from;
    }

    public String to() {
      return to;
    }

    public long timestamp() {
      return timestamp;
    }
  }

  /** The bounded ring of recent failover events. */
  private static final class FailoverLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<FailoverEvent> events = new ArrayList<>();
  }
}
