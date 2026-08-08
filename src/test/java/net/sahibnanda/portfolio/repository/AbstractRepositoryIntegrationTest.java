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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

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
  // JVM/test run and shared across every subclass, rather than being
  // stopped/restarted per test class by JUnit's @Container lifecycle. This
  // base class is extended by multiple test classes; with @Container's
  // per-class start/stop lifecycle, the container would be stopped after
  // the first test class finished and restarted with a new port for the
  // next, while Spring's test context cache kept reusing the first class's
  // ApplicationContext (and its DataSource pointed at the now-stopped
  // container's old port) -- causing spurious "connection refused"
  // failures. Starting it once here and never stopping it (Ryuk reaps it
  // at JVM exit) avoids that.
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  // Same singleton rationale as POSTGRES above. Kafka goes through genuine
  // Spring Boot autoconfiguration (spring.kafka.* -> KafkaTemplate/
  // KafkaAdmin/ConsumerFactory beans), so @ServiceConnection alone wires
  // it up -- no @DynamicPropertySource needed, unlike Valkey/OpenSearch
  // below. Runs in KRaft mode by default (no .withKraft() call exists on
  // this class). Deliberately NOT apache/kafka:3.9.0 (README's local-dev
  // tag) -- that image's bundled startup script rejects the extra
  // internal "BROKER" listener this Testcontainers module injects
  // ("advertised.listeners cannot use the nonroutable meta-address
  // 0.0.0.0"); 4.1.0 doesn't have this problem.
  @ServiceConnection
  static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

  // Same singleton rationale as POSTGRES above. Valkey is wired through
  // this app's own ValkeyProperties (@ConfigurationProperties, not Spring
  // Data Redis), so there's no @ServiceConnection support for it --
  // GenericContainer + @DynamicPropertySource below is the only option.
  // ValkeyCache's constructor pings the server synchronously and fails
  // fast, so this container must be fully ready -- not just port-open --
  // before Spring constructs that bean; an explicit log-message wait is
  // used instead of the bare default port-listening wait for that reason.
  static final GenericContainer<?> VALKEY = new GenericContainer<>(
      DockerImageName.parse("valkey/valkey:8.1.9-alpine"))
      .withExposedPorts(6379).waitingFor(
          Wait.forLogMessage(".*Ready to accept connections tcp.*\\n", 1));

  // Same singleton rationale as POSTGRES above. OpenSearch is wired
  // through this app's own OpenSearchProperties and the raw
  // OpenSearchClient (not Spring's Elasticsearch autoconfiguration), so
  // there's no @ServiceConnection support for it either -- GenericContainer
  // + @DynamicPropertySource below is the only option. OpenSearchAPI's
  // connectivity is lazy (no eager ping in its constructor), but a bare
  // listening-port wait can still return before the cluster can actually
  // serve requests, so this waits on a real HTTP health check instead.
  // Security plugin disabled to match this project's own documented
  // local-dev OpenSearch container pattern (see README).
  static final GenericContainer<?> OPENSEARCH = new GenericContainer<>(
      DockerImageName.parse("opensearchproject/opensearch:3.5.0"))
      .withExposedPorts(9200).withEnv("discovery.type", "single-node")
      .withEnv("DISABLE_SECURITY_PLUGIN", "true").waitingFor(
          Wait.forHttp("/_cluster/health").forPort(9200).forStatusCode(200));

  static {
    POSTGRES.start();
    KAFKA.start();
    VALKEY.start();
    OPENSEARCH.start();
  }

  @DynamicPropertySource
  static void registerDynamicProperties(
      final DynamicPropertyRegistry registry) {
    registry.add("valkey.host", VALKEY::getHost);
    registry.add("valkey.port", () -> VALKEY.getMappedPort(6379));
    registry.add("valkey.use-tls", () -> false);

    registry.add("opensearch.host", OPENSEARCH::getHost);
    registry.add("opensearch.port", () -> OPENSEARCH.getMappedPort(9200));
    registry.add("opensearch.https", () -> false);
  }

  @Autowired
  protected DSLContext dslContext;

  @BeforeEach
  @AfterEach
  void cleanDatabase() {
    dslContext.deleteFrom(Tables.CHATS).execute();
    dslContext.deleteFrom(Tables.ANONYMOUS_CHATS).execute();
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
