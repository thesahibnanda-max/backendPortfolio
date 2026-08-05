package net.sahibnanda.portfolio.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.api.OpenSearchAPI;
import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.exception.HealthCheckException;
import net.sahibnanda.portfolio.queue.Kafka;
import org.jooq.DSLContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically pings Postgres, Valkey, Kafka, and OpenSearch so free-tier
 * instances of any of them don't spin down from inactivity. {@link #ping()}
 * also backs the {@code /health} endpoint.
 */
@Slf4j
@Service
public final class CronPingService {

  /** Upper bound on how long any single dependency's ping may take. */
  private static final Duration PING_TIMEOUT = Duration.ofSeconds(10);

  /** Shared cache pinged to keep the Valkey instance warm. */
  private final ValkeyCache cache;

  /** jOOQ context used to keep the database instance warm. */
  private final DSLContext dslContext;

  /** Used to ping Kafka via the admin client, no topic involved. */
  private final Kafka kafka;

  /** Used to ping OpenSearch, no index involved. */
  private final OpenSearchAPI openSearchAPI;

  /** Runs the four pings concurrently. */
  private final ExecutorService executor =
      Executors.newVirtualThreadPerTaskExecutor();

  /**
   * Creates a new cron ping service.
   *
   * @param valkeyCache the cache to ping
   * @param jooqDslContext the jOOQ context used to ping the database
   * @param kafkaClient used to ping Kafka
   * @param openSearchClient used to ping OpenSearch
   */
  public CronPingService(final ValkeyCache valkeyCache,
      final DSLContext jooqDslContext, final Kafka kafkaClient,
      final OpenSearchAPI openSearchClient) {
    this.cache = valkeyCache;
    this.dslContext = jooqDslContext;
    this.kafka = kafkaClient;
    this.openSearchAPI = openSearchClient;
  }

  /**
   * Pings Postgres, Valkey, Kafka, and OpenSearch concurrently. Every
   * dependency is checked regardless of whether an earlier one failed; each
   * failure is logged individually with full detail, then combined into a
   * single {@link HealthCheckException} naming every dependency that failed, if
   * any did.
   *
   * @throws HealthCheckException if one or more dependencies failed to respond
   *         within {@link #PING_TIMEOUT}
   */
  @Scheduled(cron = "0 */5 * * * *")
  public void ping() {
    Map<String, Future<?>> pings = new LinkedHashMap<>();
    pings.put("postgres", executor.submit(this::pingDatabase));
    pings.put("valkey", executor.submit(cache::ping));
    pings.put("kafka", executor.submit(kafka::ping));
    pings.put("openSearch", executor.submit(openSearchAPI::ping));

    List<String> failed = new ArrayList<>();
    pings.forEach((name, future) -> {
      try {
        future.get(PING_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      } catch (Exception e) {
        log.error("{} keep-warm ping failed.", name, e);
        failed.add(name);
      }
    });

    if (!failed.isEmpty()) {
      throw new HealthCheckException(failed);
    }
  }

  /**
   * Checks database connectivity by running a trivial query.
   *
   * @throws org.jooq.exception.DataAccessException if the database is
   *         unreachable or the query fails
   */
  private void pingDatabase() {
    dslContext.selectOne().fetch();
  }
}
