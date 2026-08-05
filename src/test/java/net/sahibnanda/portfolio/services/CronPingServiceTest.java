package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThatCode;

import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CronPingServiceTest extends AbstractRepositoryIntegrationTest {

  @Autowired
  private CronPingService cronPingService;

  @Test
  void pingSucceedsWhenEveryDependencyIsHealthy() {
    assertThatCode(cronPingService::ping).doesNotThrowAnyException();
  }
}
