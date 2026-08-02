package net.sahibnanda.portfolio.repository;

import net.sahibnanda.portfolio.jooq.Tables;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
public abstract class AbstractRepositoryIntegrationTest {

  // Singleton container pattern: started once (statically) for the whole JVM/test run and
  // shared across every subclass, rather than being stopped/restarted per test class by
  // JUnit's @Container lifecycle. This base class is extended by multiple test classes
  // (e.g. SchemaInitializerTest, BackendPortfolioApplicationTests); with @Container's
  // per-class start/stop lifecycle, the container would be stopped after the first test
  // class finished and restarted with a new port for the next, while Spring's test context
  // cache kept reusing the first class's ApplicationContext (and its DataSource pointed at
  // the now-stopped container's old port) -- causing spurious "connection refused" failures.
  // Starting it once here and never stopping it (Ryuk reaps it at JVM exit) avoids that.
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @Autowired protected DSLContext dslContext;

  @BeforeEach
  void cleanDatabase() {
    dslContext.deleteFrom(Tables.CHATS).execute();
    dslContext.deleteFrom(Tables.USERS).execute();
  }
}
