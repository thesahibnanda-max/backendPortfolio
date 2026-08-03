package net.sahibnanda.portfolio.cache;

import static org.assertj.core.api.Assertions.assertThat;

import net.sahibnanda.portfolio.config.ValkeyProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ValkeyCacheTest {

  private static ValkeyCache valkeyCache;

  @BeforeAll
  static void setup() {
    valkeyCache =
        new ValkeyCache(new ValkeyProperties("localhost", 6379, null, null));
  }

  @Test
  void getReturnsStoredValue() {
    valkeyCache.set("greeting", "hello", 60);

    assertThat(valkeyCache.get("greeting")).isEqualTo("hello");
  }

  @Test
  void getReturnsNullForMissingKey() {
    assertThat(valkeyCache.get("missing")).isNull();
  }

  @Test
  void setOverwritesExistingValueForSameKey() {
    valkeyCache.set("key", "first", 60);
    valkeyCache.set("key", "second", 60);

    assertThat(valkeyCache.get("key")).isEqualTo("second");
  }

  @Test
  void deleteRemovesStoredValue() {
    valkeyCache.set("toDelete", "value", 60);

    valkeyCache.delete("toDelete");

    assertThat(valkeyCache.get("toDelete")).isNull();
  }

  @Test
  void getReturnsNullAfterTtlExpires() throws InterruptedException {
    valkeyCache.set("shortLived", "value", 1);

    Thread.sleep(1200);

    assertThat(valkeyCache.get("shortLived")).isNull();
  }

  @Test
  void incrementWithExpireIncrementsSequentially() {
    String key = "counter-" + System.nanoTime();

    assertThat(valkeyCache.incrementWithExpire(key, 60)).isEqualTo(1L);
    assertThat(valkeyCache.incrementWithExpire(key, 60)).isEqualTo(2L);
    assertThat(valkeyCache.incrementWithExpire(key, 60)).isEqualTo(3L);
  }

  @Test
  void incrementWithExpireSetsExpiryOnlyOnFirstIncrement()
      throws InterruptedException {
    String key = "window-" + System.nanoTime();

    valkeyCache.incrementWithExpire(key, 5);
    Thread.sleep(1200);
    valkeyCache.incrementWithExpire(key, 5);

    // If the second increment incorrectly reset the TTL back to ~5, this
    // would still be close to 5; since only the first increment should
    // set it, the remaining TTL should have counted down since then.
    assertThat(valkeyCache.ttl(key)).isLessThan(5L);
  }
}
