package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;

import net.sahibnanda.portfolio.cache.InMemoryCache;
import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.config.ValkeyProperties;
import net.sahibnanda.portfolio.utils.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CacheServiceTest {

  private static CacheService cacheService;
  private static ValkeyCache valkeyCache;

  @BeforeAll
  static void setup() {
    valkeyCache =
        new ValkeyCache(new ValkeyProperties(TestEnvironment.VALKEY_HOST,
            TestEnvironment.VALKEY_PORT, null, null,
            TestEnvironment.VALKEY_USE_TLS));
    cacheService = new CacheService(new InMemoryCache(), valkeyCache);
  }

  @Test
  void getReturnsStoredValue() {
    cacheService.set("greeting", "hello", 60, 60);

    assertThat(cacheService.get("greeting")).isEqualTo("hello");
  }

  @Test
  void getReturnsNullForMissingKey() {
    assertThat(cacheService.get("missing")).isNull();
  }

  @Test
  void getServesFromLocalTierWithoutTouchingValkey() {
    cacheService.set("localOnly", "value", 60, 60);

    // Remove it from the shared tier directly; a local-tier hit must not
    // notice, since get() should return from L1 without falling back.
    valkeyCache.delete("localOnly");

    assertThat(cacheService.get("localOnly")).isEqualTo("value");
  }

  @Test
  void getFallsBackToValkeyAndRepopulatesLocalTier() {
    // Written to Valkey directly, bypassing the local tier entirely.
    valkeyCache.set("remoteOnly", "value", 60);

    assertThat(cacheService.get("remoteOnly")).isEqualTo("value");

    // Now delete it from Valkey; the earlier get() should have
    // repopulated the local tier, so this get() must still hit L1.
    valkeyCache.delete("remoteOnly");

    assertThat(cacheService.get("remoteOnly")).isEqualTo("value");
  }

  @Test
  void deleteRemovesFromBothTiers() {
    cacheService.set("toDelete", "value", 60, 60);

    cacheService.delete("toDelete");

    assertThat(cacheService.get("toDelete")).isNull();
    assertThat(valkeyCache.get("toDelete")).isNull();
  }
}
