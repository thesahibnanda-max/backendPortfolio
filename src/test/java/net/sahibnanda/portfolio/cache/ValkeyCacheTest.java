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
}
