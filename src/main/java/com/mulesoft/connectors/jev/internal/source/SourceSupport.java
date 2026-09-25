package com.mulesoft.connectors.jev.internal.source;

import org.mule.runtime.api.store.ObjectStore;
import org.mule.runtime.api.store.ObjectStoreException;
import org.mule.sdk.api.runtime.operation.Result;
import org.mule.sdk.api.runtime.source.PollContext;

import com.mulesoft.connectors.jev.internal.util.Json;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared plumbing for the governance polling sources: emitting a JSON payload as a poll item, and reading/writing a
 * source's armed/fired flag in the stats Object Store so a breach fires once and survives a restart. A source is
 * "armed" (ready to fire) by default; it disarms when it fires and re-arms when its metric returns inside the
 * threshold.
 */
final class SourceSupport {

  private static final Logger LOGGER = LoggerFactory.getLogger(SourceSupport.class);
  private static final String ARMED_PREFIX = "source-armed:";

  private SourceSupport() {
  }

  /** Emits one JSON object as a poll item with a stable id, watermarking it with {@code watermark} when non-null. */
  static void emit(PollContext<InputStream, Void> pollContext, String id, ObjectNode payload, Long watermark) {
    pollContext.accept(item -> {
      item.setId(id);
      if (watermark != null) {
        item.setWatermark(watermark);
      }
      byte[] bytes = Json.write(payload).getBytes(StandardCharsets.UTF_8);
      item.setResult(Result.<InputStream, Void>builder().output(new ByteArrayInputStream(bytes)).build());
    });
  }

  /** A source starts armed; the flag is only ever written {@code false} on fire and {@code true} on re-arm. */
  static boolean isArmed(ObjectStore<java.io.Serializable> store, String sourceKey) {
    String key = ARMED_PREFIX + sourceKey;
    try {
      if (store != null && store.contains(key)) {
        Object value = store.retrieve(key);
        return !(value instanceof Boolean) || (Boolean) value;
      }
    } catch (ObjectStoreException | RuntimeException e) {
      LOGGER.debug("Armed-state read failed for {}; assuming armed", key, e);
    }
    return true;
  }

  static void setArmed(ObjectStore<java.io.Serializable> store, String sourceKey, boolean armed) {
    if (store == null) {
      return;
    }
    String key = ARMED_PREFIX + sourceKey;
    try {
      if (store.contains(key)) {
        store.remove(key);
      }
      store.store(key, armed);
    } catch (ObjectStoreException | RuntimeException e) {
      LOGGER.debug("Armed-state write failed for {}", key, e);
    }
  }
}
