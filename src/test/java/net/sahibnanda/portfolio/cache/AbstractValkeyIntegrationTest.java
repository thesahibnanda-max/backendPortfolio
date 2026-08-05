package net.sahibnanda.portfolio.cache;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

// Shared, started-once Valkey container for every direct-construction test
// class that needs Valkey but has no Spring context to protect (unlike
// AbstractRepositoryIntegrationTest's Postgres/Kafka/Valkey/OpenSearch,
// which are singletons specifically to preserve Spring's test-context
// cache). Here it's purely about concurrency: with junit-platform.properties
// running test classes concurrently, five independent test classes each
// starting their own Valkey container at once was enough to starve Docker
// Desktop and make ValkeyCache's fail-fast synchronous ping time out.
// Sharing one container across all of them removes that contention while
// still requiring zero manual setup and dying automatically at JVM exit.
public abstract class AbstractValkeyIntegrationTest {

  protected static final GenericContainer<?> VALKEY = new GenericContainer<>(
      DockerImageName.parse("valkey/valkey:8.1.9-alpine"))
      .withExposedPorts(6379).waitingFor(
          Wait.forLogMessage(".*Ready to accept connections tcp.*\\n", 1));

  static {
    VALKEY.start();
  }
}
