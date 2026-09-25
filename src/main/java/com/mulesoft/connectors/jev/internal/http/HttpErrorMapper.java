package com.mulesoft.connectors.jev.internal.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.mulesoft.connectors.jev.internal.error.JevErrorType;
import com.mulesoft.connectors.jev.internal.util.Json;

import org.mule.sdk.api.exception.ModuleException;

/**
 * Maps a provider HTTP response (status + body) onto a typed {@code JEV:*} error, extracting the
 * human-readable message the way the official TypeSafe SDK does.
 */
public final class HttpErrorMapper {

  private HttpErrorMapper() {}

  /** Classifies an HTTP status into a Jev error type. */
  public static JevErrorType classify(int status) {
    switch (status) {
      case 401:
      case 403:
        return JevErrorType.UNAUTHORIZED;
      case 408:
        return JevErrorType.TIMEOUT;
      case 429:
        return JevErrorType.RATE_LIMITED;
      case 400:
      case 422:
        return JevErrorType.PROVIDER_VALIDATION;
      case 503:
      case 529:
        return JevErrorType.OVERLOADED;
      default:
        if (status >= 500) {
          return JevErrorType.PROVIDER_ERROR;
        }
        if (status >= 400) {
          return JevErrorType.PROVIDER_VALIDATION;
        }
        return JevErrorType.INVALID_RESPONSE;
    }
  }

  /** Builds the typed exception for a non-2xx response. */
  public static ModuleException toException(int status, String body) {
    JevErrorType type = classify(status);
    String message = extractMessage(body).orElse("HTTP " + status);
    return new ModuleException(type.name() + " (HTTP " + status + "): " + message, type);
  }

  /**
   * Extracts the provider's error message following the documented order:
   * {@code error} (string) → {@code error.message} → {@code message} → {@code detail} (string) →
   * {@code detail.message} → {@code detail[].msg} joined with their {@code loc} paths.
   */
  public static Optional<String> extractMessage(String body) {
    if (body == null || body.isBlank()) {
      return Optional.empty();
    }
    JsonNode root;
    try {
      root = Json.read(body);
    } catch (RuntimeException e) {
      return Optional.empty();
    }
    if (root == null || !root.isObject()) {
      return Optional.empty();
    }

    JsonNode error = root.get("error");
    if (error != null) {
      if (error.isTextual() && !error.asText().isBlank()) {
        return Optional.of(error.asText());
      }
      JsonNode errorMessage = error.path("message");
      if (errorMessage.isTextual() && !errorMessage.asText().isBlank()) {
        return Optional.of(errorMessage.asText());
      }
    }

    JsonNode message = root.get("message");
    if (message != null && message.isTextual() && !message.asText().isBlank()) {
      return Optional.of(message.asText());
    }

    JsonNode detail = root.get("detail");
    if (detail != null) {
      if (detail.isTextual() && !detail.asText().isBlank()) {
        return Optional.of(detail.asText());
      }
      JsonNode detailMessage = detail.path("message");
      if (detailMessage.isTextual() && !detailMessage.asText().isBlank()) {
        return Optional.of(detailMessage.asText());
      }
      if (detail.isArray()) {
        String joined = joinValidationDetails(detail);
        if (!joined.isBlank()) {
          return Optional.of(joined);
        }
      }
    }
    return Optional.empty();
  }

  private static String joinValidationDetails(JsonNode detailArray) {
    List<String> parts = new ArrayList<>();
    for (JsonNode item : detailArray) {
      String msg = item.path("msg").asText("");
      if (msg.isBlank()) {
        continue;
      }
      JsonNode loc = item.get("loc");
      if (loc != null && loc.isArray() && !loc.isEmpty()) {
        List<String> locParts = new ArrayList<>();
        for (JsonNode l : loc) {
          locParts.add(l.asText());
        }
        parts.add(String.join(".", locParts) + ": " + msg);
      } else {
        parts.add(msg);
      }
    }
    return String.join("; ", parts);
  }
}
