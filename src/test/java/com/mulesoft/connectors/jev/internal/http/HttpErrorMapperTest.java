package com.mulesoft.connectors.jev.internal.http;

import org.mule.sdk.api.exception.ModuleException;

import com.mulesoft.connectors.jev.internal.error.JevErrorType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every HTTP status in the error table maps to the documented {@code JEV:*} error. */
class HttpErrorMapperTest {

  @ParameterizedTest
  @CsvSource({"401, UNAUTHORIZED", "403, UNAUTHORIZED", "408, TIMEOUT", "429, RATE_LIMITED", "400, PROVIDER_VALIDATION",
      "422, PROVIDER_VALIDATION", "503, OVERLOADED", "529, OVERLOADED", "500, PROVIDER_ERROR", "502, PROVIDER_ERROR",
      "504, PROVIDER_ERROR", "404, PROVIDER_VALIDATION", "418, PROVIDER_VALIDATION", "302, INVALID_RESPONSE",
      "204, INVALID_RESPONSE"})
  void classifiesEveryStatus(int status, JevErrorType expected) {
    assertEquals(expected, HttpErrorMapper.classify(status));
  }

  @Test
  void buildsTypedExceptionWithExtractedMessage() {
    ModuleException e = HttpErrorMapper.toException(429, "{\"error\":{\"message\":\"slow down\"}}");
    assertEquals(JevErrorType.RATE_LIMITED, e.getType());
    assertTrue(e.getMessage().contains("slow down"));
  }

  @Test
  void extractsMessageInDocumentedOrder() {
    assertEquals("top", HttpErrorMapper.extractMessage("{\"error\":\"top\"}").orElseThrow());
    assertEquals("nested", HttpErrorMapper.extractMessage("{\"error\":{\"message\":\"nested\"}}").orElseThrow());
    assertEquals("plain", HttpErrorMapper.extractMessage("{\"message\":\"plain\"}").orElseThrow());
    assertEquals("d", HttpErrorMapper.extractMessage("{\"detail\":\"d\"}").orElseThrow());
    assertEquals("body.name: required", HttpErrorMapper
        .extractMessage("{\"detail\":[{\"loc\":[\"body\",\"name\"],\"msg\":\"required\"}]}").orElseThrow());
  }

  @Test
  void returnsEmptyForUnparseableOrEmptyBody() {
    assertTrue(HttpErrorMapper.extractMessage("not json").isEmpty());
    assertTrue(HttpErrorMapper.extractMessage("").isEmpty());
    assertTrue(HttpErrorMapper.extractMessage(null).isEmpty());
  }
}
