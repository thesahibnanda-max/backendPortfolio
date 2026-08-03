package net.sahibnanda.portfolio.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InMemoryCacheTest {

  private final InMemoryCache cache = new InMemoryCache();

  @Test
  void getReturnsStoredValue() {
    cache.set("greeting", "hello", 60);

    assertThat(cache.get("greeting", String.class)).isEqualTo("hello");
  }

  @Test
  void getReturnsNullForMissingKey() {
    assertThat(cache.get("missing", String.class)).isNull();
  }

  @Test
  void getReturnsNullWhenStoredValueIsWrongType() {
    cache.set("count", 42, 60);

    assertThat(cache.get("count", String.class)).isNull();
  }

  @Test
  void setOverwritesExistingValueForSameKey() {
    cache.set("key", "first", 60);
    cache.set("key", "second", 60);

    assertThat(cache.get("key", String.class)).isEqualTo("second");
  }

  @Test
  void getReturnsNullAfterTtlExpires() throws InterruptedException {
    cache.set("shortLived", "value", 1);

    // Caffeine checks expiry lazily on access; sleep comfortably past the
    // 1-second TTL rather than adding a test-only Ticker seam to a class
    // this small.
    Thread.sleep(1200);

    assertThat(cache.get("shortLived", String.class)).isNull();
  }
}
