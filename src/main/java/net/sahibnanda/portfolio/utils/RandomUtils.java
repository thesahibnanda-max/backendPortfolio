package net.sahibnanda.portfolio.utils;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;

/** Weighted and range-bounded random selection. */
@UtilityClass
public final class RandomUtils {

  /**
   * Picks a key at random, weighted by its associated value.
   *
   * @param weights each candidate mapped to its relative weight
   * @param <T> the key type
   * @return a randomly chosen key, weighted by {@code weights}
   * @throws IllegalArgumentException if {@code weights} is null, empty, or its
   *         values don't sum to a positive total
   */
  public <T> T weightedRandom(final Map<T, Integer> weights) {
    if (weights == null || weights.isEmpty()) {
      throw new IllegalArgumentException("weights must not be empty.");
    }
    int totalWeight =
        weights.values().stream().mapToInt(Integer::intValue).sum();
    if (totalWeight <= 0) {
      throw new IllegalArgumentException(
          "weights must sum to a positive total.");
    }

    int roll = ThreadLocalRandom.current().nextInt(totalWeight);
    int cumulative = 0;
    for (Map.Entry<T, Integer> entry : weights.entrySet()) {
      cumulative += entry.getValue();
      if (roll < cumulative) {
        return entry.getKey();
      }
    }
    throw new IllegalStateException("Unreachable: weighted selection failed.");
  }

  /**
   * Returns a random value in {@code [min, max)}, or exactly {@code min} if
   * {@code min == max}.
   *
   * @param min the inclusive lower bound
   * @param max the exclusive upper bound
   * @return a random value in the given range
   * @throws IllegalArgumentException if {@code min > max}
   */
  public double randomInRange(final double min, final double max) {
    if (min > max) {
      throw new IllegalArgumentException("min must not be greater than max.");
    }
    if (min == max) {
      return min;
    }
    return ThreadLocalRandom.current().nextDouble(min, max);
  }
}
