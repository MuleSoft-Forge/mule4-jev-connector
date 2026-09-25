package com.mulesoft.connectors.jev.internal.source;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriftMathTest {

  @Test
  void identicalDistributionsHaveZeroDivergence() {
    Map<String, Long> a = Map.of("yes", 60L, "no", 40L);
    Map<String, Long> b = Map.of("yes", 30L, "no", 20L);
    // Same proportions at different totals still match exactly.
    assertEquals(0.0, DriftMath.jensenShannon(a, b), 1e-12);
  }

  @Test
  void disjointDistributionsAreMaximallyDivergent() {
    Map<String, Long> a = Map.of("yes", 100L);
    Map<String, Long> b = Map.of("no", 100L);
    // Base-2 Jensen-Shannon is bounded at 1 for fully disjoint supports.
    assertEquals(1.0, DriftMath.jensenShannon(a, b), 1e-9);
  }

  @Test
  void divergenceGrowsAsDistributionsSeparate() {
    Map<String, Long> baseline = Map.of("a", 50L, "b", 50L);
    double small = DriftMath.jensenShannon(baseline, Map.of("a", 55L, "b", 45L));
    double large = DriftMath.jensenShannon(baseline, Map.of("a", 90L, "b", 10L));
    assertTrue(small < large, "a bigger shift should score higher");
    assertTrue(small > 0.0, "any shift should be positive");
    assertTrue(large <= 1.0, "divergence stays bounded at 1");
  }

  @Test
  void emptyOrOneSidedInputsAreZero() {
    assertEquals(0.0, DriftMath.jensenShannon(Map.of(), Map.of("a", 1L)), 1e-12);
    assertEquals(0.0, DriftMath.jensenShannon(Map.of("a", 1L), Map.of()), 1e-12);
  }
}
