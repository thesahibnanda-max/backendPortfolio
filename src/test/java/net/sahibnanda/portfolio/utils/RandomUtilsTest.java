package net.sahibnanda.portfolio.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RandomUtilsTest {

  @Test
  void weightedRandomPicksRoughlyByWeight() {
    Map<String, Integer> weights = Map.of("a", 50, "b", 30, "c", 20);
    Map<String, AtomicInteger> counts = new HashMap<>();
    int trials = 100_000;

    for (int i = 0; i < trials; i++) {
      String picked = RandomUtils.weightedRandom(weights);
      counts.computeIfAbsent(picked, k -> new AtomicInteger())
          .incrementAndGet();
    }

    assertThat(counts.get("a").get()).isBetween(45_000, 55_000);
    assertThat(counts.get("b").get()).isBetween(25_000, 35_000);
    assertThat(counts.get("c").get()).isBetween(15_000, 25_000);
  }

  @Test
  void weightedRandomRejectsEmptyOrNullWeights() {
    assertThatThrownBy(() -> RandomUtils.weightedRandom(Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RandomUtils.weightedRandom(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void randomInRangeStaysWithinBounds() {
    for (int i = 0; i < 10_000; i++) {
      double value = RandomUtils.randomInRange(0.8, 1.4);
      assertThat(value).isGreaterThanOrEqualTo(0.8).isLessThan(1.4);
    }
  }

  @Test
  void randomInRangeReturnsMinWhenMinEqualsMax() {
    assertThat(RandomUtils.randomInRange(1.0, 1.0)).isEqualTo(1.0);
  }

  @Test
  void randomInRangeRejectsMinGreaterThanMax() {
    assertThatThrownBy(() -> RandomUtils.randomInRange(2.0, 1.0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
