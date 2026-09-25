package com.mulesoft.connectors.jev.internal.cache;

import com.mulesoft.connectors.jev.api.attributes.DecisionAttributes;
import com.mulesoft.connectors.jev.internal.domain.DecisionRequest;
import com.mulesoft.connectors.jev.internal.engine.DecisionOutcome;
import com.mulesoft.connectors.jev.internal.support.InMemoryObjectStore;
import com.mulesoft.connectors.jev.internal.util.Json;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecisionCacheTest {

  private static final long HOUR = 3_600_000L;

  private final DecisionCache cache = new DecisionCache(new InMemoryObjectStore());

  @Test
  void keyIsStableForEqualRequestsAndSensitiveToItsParts() {
    DecisionRequest a = request("{\"a\":1,\"b\":2}", "{\"q\":{\"type\":\"noul\"}}");
    DecisionRequest b = request("{\"b\":2,\"a\":1}", "{\"q\":{\"type\":\"noul\"}}");
    assertEquals(cache.keyFor("openrouter", "m1", a), cache.keyFor("openrouter", "m1", b));
    assertNotEquals(cache.keyFor("openrouter", "m1", a), cache.keyFor("typesafe", "m1", a));
    assertNotEquals(cache.keyFor("openrouter", "m1", a), cache.keyFor("openrouter", "m2", a));
    assertNotEquals(cache.keyFor("openrouter", "m1", a),
        cache.keyFor("openrouter", "m1", request("{\"a\":9}", "{\"q\":{\"type\":\"noul\"}}")));
  }

  @Test
  void storesAndServesAFreshHitMarkedCacheHit() {
    DecisionRequest request = request("{\"a\":1}", "{\"q\":{\"type\":\"noul\"}}");
    String key = cache.keyFor("openrouter", "m1", request);
    cache.put(key, outcome("{\"q\":{\"noul\":true}}"));

    Optional<DecisionOutcome> hit = cache.lookup(key, HOUR);
    assertTrue(hit.isPresent());
    assertTrue(hit.get().attributes().isCacheHit());
    assertEquals("openrouter", hit.get().attributes().getProvider());
    assertEquals(true, hit.get().payload().path("q").path("noul").asBoolean());
  }

  @Test
  void missReturnsEmpty() {
    assertFalse(cache.lookup("nope", HOUR).isPresent());
  }

  @Test
  void expiredEntryIsEvictedOnRead() {
    DecisionRequest request = request("{\"a\":1}", "{\"q\":{\"type\":\"noul\"}}");
    String key = cache.keyFor("openrouter", "m1", request);
    cache.put(key, outcome("{\"q\":{\"noul\":true}}"));
    // A zero-length TTL makes any stored entry immediately stale.
    assertFalse(cache.lookup(key, 0L).isPresent());
    // The stale entry was removed, so it is still a miss with a generous TTL.
    assertFalse(cache.lookup(key, HOUR).isPresent());
  }

  private static DecisionRequest request(String state, String questions) {
    return new DecisionRequest(Json.read(state), "m1", (ObjectNode) Json.read(questions), Map.of(), "set-1", "1");
  }

  private static DecisionOutcome outcome(String answers) {
    DecisionAttributes attributes = DecisionAttributes.builder().provider("openrouter").requestedModel("m1")
        .model("m1-2024").questionSetId("set-1").questionSetVersion("1").stateHash("abc").build();
    return new DecisionOutcome((ObjectNode) Json.read(answers), attributes);
  }
}
