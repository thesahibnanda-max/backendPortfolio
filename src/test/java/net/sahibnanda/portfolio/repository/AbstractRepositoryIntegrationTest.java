package net.sahibnanda.portfolio.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.sahibnanda.portfolio.jooq.Tables;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.PostgreSQLContainer;

// Every subclass shares the one singleton Postgres container below and
// truncates its tables in @BeforeEach/@AfterEach; several subclasses also
// reuse fixed usernames. Running two subclasses concurrently would race on
// both. This lock serializes all subclasses against each other (via the
// shared resource name) while leaving them free to run in parallel with
// every other test class in the suite -- see junit-platform.properties.
@SpringBootTest
@ResourceLock(value = "shared-postgres-database",
    mode = ResourceAccessMode.READ_WRITE)
public abstract class AbstractRepositoryIntegrationTest {

  // Singleton container pattern: started once (statically) for the whole
  // JVM/test run and
  // shared across every subclass, rather than being stopped/restarted per test
  // class by
  // JUnit's @Container lifecycle. This base class is extended by multiple test
  // classes
  // (e.g. SchemaInitializerTest, BackendPortfolioApplicationTests); with
  // @Container's
  // per-class start/stop lifecycle, the container would be stopped after the
  // first test
  // class finished and restarted with a new port for the next, while Spring's
  // test context
  // cache kept reusing the first class's ApplicationContext (and its DataSource
  // pointed at
  // the now-stopped container's old port) -- causing spurious "connection
  // refused" failures.
  // Starting it once here and never stopping it (Ryuk reaps it at JVM exit)
  // avoids that.
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @Autowired
  protected DSLContext dslContext;

  @BeforeEach
  @AfterEach
  void cleanDatabase() {
    dslContext.deleteFrom(Tables.CHATS).execute();
    dslContext.deleteFrom(Tables.USERS).execute();
  }

  /**
   * Executes a SQL script from the classpath against the test database,
   * mirroring how
   * {@link net.sahibnanda.portfolio.repository.init.SchemaInitializer} applies
   * {@code schema.sql}. Call from a subclass {@code @BeforeEach} so it runs
   * after {@link #cleanDatabase()}.
   *
   * @param classpathLocation classpath-relative path to the SQL script
   */
  protected void loadFixtureScript(final String classpathLocation) {
    Resource resource = new ClassPathResource(classpathLocation);
    try (InputStream inputStream = resource.getInputStream()) {
      String script =
          StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
      dslContext.parser().parse(script).executeBatch();
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to load fixture script: " + classpathLocation, e);
    }
  }
}
