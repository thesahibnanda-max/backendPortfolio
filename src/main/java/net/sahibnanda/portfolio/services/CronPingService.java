package net.sahibnanda.portfolio.services;

import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.cache.ValkeyCache;
import org.jooq.DSLContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically pings Valkey and the database so free-tier instances of either
 * don't spin down from inactivity. {@link #pingDatabase()} also backs a
 * {@code /health} endpoint.
 */
@Slf4j
@Service
public final class CronPingService {

  /** Shared cache pinged to keep the Valkey instance warm. */
  private final ValkeyCache cache;

  /** jOOQ context used to keep the database instance warm. */
  private final DSLContext dslContext;

  /**
   * Creates a new cron ping service.
   *
   * @param valkeyCache the cache to ping
   * @param jooqDslContext the jOOQ context used to ping the database
   */
  public CronPingService(final ValkeyCache valkeyCache,
      final DSLContext jooqDslContext) {
    this.cache = valkeyCache;
    this.dslContext = jooqDslContext;
  }

  /**
   * Pings Valkey and the database. Each ping is independent: a failure in one
   * is logged and does not prevent the other from running, and neither failure
   * stops future scheduled runs.
   */
  @Scheduled(cron = "0 */5 * * * *")
  public void ping() {
    try {
      cache.ping();
    } catch (RuntimeException e) {
      log.error("Valkey keep-warm ping failed.", e);
    }

    try {
      pingDatabase();
    } catch (RuntimeException e) {
      log.error("Database keep-warm ping failed.", e);
    }
  }

  /**
   * Checks database connectivity by running a trivial query.
   *
   * @throws org.jooq.exception.DataAccessException if the database is
   *         unreachable or the query fails
   */
  public void pingDatabase() {
    dslContext.selectOne().fetch();
  }
}
