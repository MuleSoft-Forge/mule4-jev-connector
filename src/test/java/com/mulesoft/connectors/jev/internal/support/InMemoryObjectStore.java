package com.mulesoft.connectors.jev.internal.support;

import org.mule.runtime.api.store.ObjectDoesNotExistException;
import org.mule.runtime.api.store.ObjectStore;
import org.mule.runtime.api.store.ObjectStoreException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, thread-safe in-memory {@link ObjectStore} for tests. It stands in for the runtime's Object Store so the
 * cache, budget guard and stats recorder can be exercised without a Mule context. {@code store} overwrites an existing
 * key, matching how the production code always removes before storing.
 */
public final class InMemoryObjectStore implements ObjectStore<Serializable> {

  private final Map<String, Serializable> map = new LinkedHashMap<>();

  @Override
  public synchronized boolean contains(String key) {
    return map.containsKey(key);
  }

  @Override
  public synchronized void store(String key, Serializable value) {
    map.put(key, value);
  }

  @Override
  public synchronized Serializable retrieve(String key) throws ObjectStoreException {
    if (!map.containsKey(key)) {
      throw new ObjectDoesNotExistException();
    }
    return map.get(key);
  }

  @Override
  public synchronized Serializable remove(String key) throws ObjectStoreException {
    if (!map.containsKey(key)) {
      throw new ObjectDoesNotExistException();
    }
    return map.remove(key);
  }

  @Override
  public boolean isPersistent() {
    return false;
  }

  @Override
  public synchronized void clear() {
    map.clear();
  }

  @Override
  public void open() {
    // no-op
  }

  @Override
  public void close() {
    // no-op
  }

  @Override
  public synchronized List<String> allKeys() {
    return new ArrayList<>(map.keySet());
  }

  @Override
  public synchronized Map<String, Serializable> retrieveAll() {
    return new LinkedHashMap<>(map);
  }
}
