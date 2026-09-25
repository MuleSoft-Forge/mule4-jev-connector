package com.mulesoft.connectors.jev.internal.source;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The distribution-shift metric for the drift source. Jensen–Shannon divergence (base-2) is bounded to {@code [0, 1]},
 * is symmetric, and needs no smoothing for zero-probability bins, so it is a stable, thresholdable measure of how far a
 * window's choice/level distribution has moved from its baseline. PSI, the common alternative, is unbounded and
 * undefined on empty bins, which makes a fixed threshold brittle.
 */
final class DriftMath {

  private DriftMath() {
  }

  /**
   * Jensen–Shannon divergence between two count distributions, in bits (base-2), so the result is in {@code [0, 1]}.
   * Empty or single-sided inputs return {@code 0} (nothing to compare / no overlap information).
   */
  static double jensenShannon(Map<String, Long> baseline, Map<String, Long> current) {
    long baselineTotal = total(baseline);
    long currentTotal = total(current);
    if (baselineTotal == 0 || currentTotal == 0) {
      return 0.0;
    }
    Set<String> keys = new HashSet<>();
    keys.addAll(baseline.keySet());
    keys.addAll(current.keySet());

    double divergence = 0.0;
    for (String key : keys) {
      double p = baseline.getOrDefault(key, 0L) / (double) baselineTotal;
      double q = current.getOrDefault(key, 0L) / (double) currentTotal;
      double m = 0.5 * (p + q);
      divergence += 0.5 * klTerm(p, m) + 0.5 * klTerm(q, m);
    }
    // Clamp tiny floating-point overshoot so a threshold comparison stays well-defined.
    return Math.max(0.0, Math.min(1.0, divergence));
  }

  private static double klTerm(double value, double mean) {
    if (value <= 0.0 || mean <= 0.0) {
      return 0.0;
    }
    return value * (Math.log(value / mean) / Math.log(2));
  }

  private static long total(Map<String, Long> distribution) {
    long sum = 0;
    for (Long count : distribution.values()) {
      if (count != null) {
        sum += count;
      }
    }
    return sum;
  }
}
