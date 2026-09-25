package com.mulesoft.connectors.jev.internal.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Central JSON helper. Jackson is the connector's only JSON library. A second, canonical mapper
 * (keys sorted) is used to hash requests and state so cache keys and audit hashes are stable.
 */
public final class Json {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final ObjectMapper CANONICAL = new ObjectMapper()
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
      .configure(SerializationFeature.INDENT_OUTPUT, false);

  private Json() {}

  public static ObjectMapper mapper() {
    return MAPPER;
  }

  public static ObjectNode object() {
    return MAPPER.createObjectNode();
  }

  public static JsonNode read(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static JsonNode read(InputStream json) {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String write(JsonNode node) {
    try {
      return MAPPER.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Serializes a node with keys sorted alphabetically at every level, so logically-equal payloads
   * produce byte-identical output. Used for cache keys and state hashes.
   */
  public static String canonical(JsonNode node) {
    try {
      Object normalized = CANONICAL.treeToValue(node, Object.class);
      return CANONICAL.writeValueAsString(normalized);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** SHA-256 of the canonical serialization of a node, lowercase hex. */
  public static String sha256(JsonNode node) {
    return sha256(canonical(node));
  }

  /** SHA-256 of a string, lowercase hex. */
  public static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16));
        hex.append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
