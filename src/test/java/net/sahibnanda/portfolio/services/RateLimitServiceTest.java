package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.config.ValkeyProperties;
import net.sahibnanda.portfolio.utils.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

  private static RateLimitService rateLimitService;

  @BeforeAll
  static void setup() {
    ValkeyCache valkeyCache =
        new ValkeyCache(new ValkeyProperties(TestEnvironment.VALKEY_HOST,
            TestEnvironment.VALKEY_PORT, null, null));
    rateLimitService = new RateLimitService(valkeyCache);
  }

  @Test
  void isAllowedReturnsTrueUnderTheLimit() {
    String key = "rate-test-under-" + System.nanoTime();

    assertThat(rateLimitService.isAllowed(key, 3, 60)).isTrue();
    assertThat(rateLimitService.isAllowed(key, 3, 60)).isTrue();
    assertThat(rateLimitService.isAllowed(key, 3, 60)).isTrue();
  }

  @Test
  void isAllowedReturnsFalseOverTheLimit() {
    String key = "rate-test-over-" + System.nanoTime();
    rateLimitService.isAllowed(key, 2, 60);
    rateLimitService.isAllowed(key, 2, 60);

    assertThat(rateLimitService.isAllowed(key, 2, 60)).isFalse();
  }

  @Test
  void isAllowedRejectsBlankKey() {
    assertThatThrownBy(() -> rateLimitService.isAllowed("", 5, 60))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void isAllowedResetsAfterTheWindowExpires() throws InterruptedException {
    String key = "rate-test-reset-" + System.nanoTime();
    rateLimitService.isAllowed(key, 1, 1);
    assertThat(rateLimitService.isAllowed(key, 1, 1)).isFalse();

    Thread.sleep(1200);

    assertThat(rateLimitService.isAllowed(key, 1, 1)).isTrue();
  }
}
