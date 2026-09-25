package com.mulesoft.connectors.jev.internal.cache;

import org.mule.runtime.api.store.ObjectStore;
import org.mule.runtime.api.store.ObjectStoreException;
import org.mule.runtime.api.store.ObjectStoreManager;
import org.mule.runtime.api.store.ObjectStoreSettings;

import com.mulesoft.connectors.jev.api.attributes.DecisionAttributes;
import com.mulesoft.connectors.jev.api.attributes.TokenUsage;
import com.mulesoft.connectors.jev.api.attributes.TraceEntry;
import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.engine.DecisionOutcome;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A cluster-aware decision cache. It keys each entry by a SHA-256 of the route, model, state and questions, so
 * identical decisions on any node are served without a billed call. The store is an in-memory, distributed Object
 * Store, so entries are shared across CloudHub 2.0 replicas but never survive a restart (a stale decision is worse than
 * a cheap re-evaluation). Freshness is enforced per lookup against the configured TTL, so a config change takes effect
 * immediately without draining the store.
 *
 * <p>
 * Only the enriched answers and the identity needed to rebuild attributes are stored — never raw request bodies, keys
 * or secrets.
 */
public final class DecisionCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionCache.class);
  private static final String STORE_NAME = "_jevDecisionCache";

  private final ObjectStore<Serializable> store;

  public DecisionCache(ObjectStore<Serializable> store) {
    this.store = store;
  }

  /** Builds a shared, distributed, non-persistent cache store from the runtime's Object Store manager. */
  public static DecisionCache create(ObjectStoreManager manager) {
    ObjectStore<Serializable> store = manager.getOrCreateObjectStore(STORE_NAME,
        ObjectStoreSettings.builder().persistent(false).maxEntries(10_000)
            .entryTtl(java.util.concurrent.TimeUnit.DAYS.toMillis(1)).expirationInterval(60_000L).build());
    return new DecisionCache(store);
  }

  /**
   * The stable cache key for a request on a route: SHA-256 of route + model + canonical(state) + canonical(questions).
   */
  public String keyFor(String route, String model, DecisionRequest request) {
    ObjectNode key = Json.object();
    key.put("route", route == null ? "" : route);
    key.put("model", model == null ? "" : model);
    if (request.state() == null) {
      key.putNull("state");
    } else {
      key.set("state", request.state());
    }
    key.set("questions", request.questions());
    return Json.sha256(key);
  }

  /**
   * Returns a fresh cached decision for {@code key}, or empty on a miss or an entry older than {@code ttlMillis}. A
   * stale entry is removed on read so the store self-cleans.
   */
  public Optional<DecisionOutcome> lookup(String key, long ttlMillis) {
    try {
      if (!store.contains(key)) {
        return Optional.empty();
      }
      CachedDecision entry = (CachedDecision) store.retrieve(key);
      if (System.currentTimeMillis() - entry.storedAtMs >= ttlMillis) {
        remove(key);
        return Optional.empty();
      }
      return Optional.of(entry.toOutcome());
    } catch (ObjectStoreException | RuntimeException e) {
      // A cache is an optimisation: a store hiccup must never fail a decision, only skip the cache.
      LOGGER.debug("Decision cache lookup failed for key {}; treating as a miss", key, e);
      return Optional.empty();
    }
  }

  /** Stores the outcome under {@code key}, overwriting any existing entry. Failures are logged and swallowed. */
  public void put(String key, DecisionOutcome outcome) {
    try {
      if (store.contains(key)) {
        store.remove(key);
      }
      store.store(key, CachedDecision.of(outcome));
    } catch (ObjectStoreException | RuntimeException e) {
      LOGGER.debug("Decision cache store failed for key {}; entry not cached", key, e);
    }
  }

  private void remove(String key) {
    try {
      store.remove(key);
    } catch (ObjectStoreException | RuntimeException e) {
      LOGGER.debug("Decision cache eviction failed for key {}", key, e);
    }
  }

  /** The serializable cache value: the enriched answers plus the identity needed to rebuild attributes on a hit. */
  private static final class CachedDecision implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String answersJson;
    private final String provider;
    private final String requestedModel;
    private final String model;
    private final String questionSetId;
    private final String questionSetVersion;
    private final String stateHash;
    private final long storedAtMs;

    private CachedDecision(String answersJson, String provider, String requestedModel, String model,
        String questionSetId, String questionSetVersion, String stateHash, long storedAtMs) {
      this.answersJson = answersJson;
      this.provider = provider;
      this.requestedModel = requestedModel;
      this.model = model;
      this.questionSetId = questionSetId;
      this.questionSetVersion = questionSetVersion;
      this.stateHash = stateHash;
      this.storedAtMs = storedAtMs;
    }

    static CachedDecision of(DecisionOutcome outcome) {
      DecisionAttributes a = outcome.attributes();
      return new CachedDecision(Json.write(outcome.payload()), a.getProvider(), a.getRequestedModel(), a.getModel(),
          a.getQuestionSetId(), a.getQuestionSetVersion(), a.getStateHash(), System.currentTimeMillis());
    }

    DecisionOutcome toOutcome() {
      ObjectNode answers = (ObjectNode) Json.read(answersJson);
      TraceEntry trace = TraceEntry.builder().provider(provider).model(model).latencyMs(0).attempts(0)
          .failedOverFrom(List.of()).questionSetId(questionSetId).build();
      DecisionAttributes attributes = DecisionAttributes.builder().provider(provider).requestedModel(requestedModel)
          .model(model).usage(new TokenUsage(0, 0)).costSource("CACHE").latencyMs(0).attempts(0).cacheHit(true)
          .questionSetId(questionSetId).questionSetVersion(questionSetVersion).stateHash(stateHash).traceEntry(trace)
          .build();
      return new DecisionOutcome(answers, attributes);
    }
  }
}
