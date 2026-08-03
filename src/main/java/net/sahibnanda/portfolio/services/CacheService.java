package net.sahibnanda.portfolio.services;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.cache.InMemoryCache;
import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.exception.CacheSetException;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Service;

/**
 * A two-tier cache: a fast per-instance {@link InMemoryCache} in front of a
 * shared {@link ValkeyCache}. Writes and deletes reach both tiers concurrently;
 * reads check the local tier first and fall back to the shared tier on a miss,
 * repopulating the local tier using Valkey's actual remaining time-to-live so
 * later reads on this instance stay fast.
 */
@Slf4j
@Service
public final class CacheService {

  /** Fast, per-instance cache tier. */
  private final InMemoryCache l1Cache;

  /** Shared, cross-instance cache tier. */
  private final ValkeyCache l2Cache;

  /** Executor used to write to and delete from both tiers concurrently. */
  private final ExecutorService executor =
      Executors.newVirtualThreadPerTaskExecutor();

  /**
   * Creates a cache service backed by the given tiers.
   *
   * @param inMemoryCache the local cache tier; must not be {@code null}
   * @param valkeyCache the shared cache tier; must not be {@code null}
   * @throws NullPointerException if either tier is {@code null}
   */
  public CacheService(final InMemoryCache inMemoryCache,
      final ValkeyCache valkeyCache) {
    this.l1Cache =
        Objects.requireNonNull(inMemoryCache, "inMemoryCache must not be null");
    this.l2Cache =
        Objects.requireNonNull(valkeyCache, "valkeyCache must not be null");
  }

  /**
   * Stores a value in both cache tiers, each with its own time-to-live.
   *
   * @param key the cache key
   * @param value the value to store
   * @param ttlSecondsL1 how long, in seconds, the local entry should live
   * @param ttlSecondsL2 how long, in seconds, the shared entry should live
   * @throws IllegalArgumentException if {@code key} is blank
   * @throws CacheSetException if either tier's write fails
   */
  public void set(final String key, final String value, final long ttlSecondsL1,
      final long ttlSecondsL2) {

    if (StringUtils.isEmpty(key)) {
      throw new IllegalArgumentException("key is required.");
    }

    Future<?> l1Write =
        executor.submit(() -> l1Cache.set(key, value, ttlSecondsL1));
    Future<?> l2Write =
        executor.submit(() -> l2Cache.set(key, value, ttlSecondsL2));

    awaitAll(l1Write, l2Write);
  }

  /**
   * Retrieves a value by key, checking the local tier first and falling back to
   * the shared tier on a miss. On a shared-tier hit, the local tier is
   * repopulated using the shared entry's actual remaining time-to-live.
   *
   * @param key the cache key
   * @return the cached value, or {@code null} if absent from both tiers
   * @throws IllegalArgumentException if {@code key} is blank
   */
  public String get(final String key) {

    if (StringUtils.isEmpty(key)) {
      throw new IllegalArgumentException("key is required.");
    }

    String localValue = l1Cache.get(key, String.class);
    if (localValue != null) {
      return localValue;
    }

    String remoteValue = l2Cache.get(key);
    if (remoteValue == null) {
      return null;
    }

    Long remainingTtl = l2Cache.ttl(key);
    if (remainingTtl != null && remainingTtl > 0) {
      l1Cache.set(key, remoteValue, remainingTtl);
    }

    return remoteValue;
  }

  /**
   * Deletes a value from both cache tiers.
   *
   * @param key the cache key
   * @throws IllegalArgumentException if {@code key} is blank
   * @throws CacheSetException if either tier's deletion fails
   */
  public void delete(final String key) {

    if (StringUtils.isEmpty(key)) {
      throw new IllegalArgumentException("key is required.");
    }

    Future<?> l1Delete = executor.submit(() -> l1Cache.delete(key));
    Future<?> l2Delete = executor.submit(() -> l2Cache.delete(key));

    awaitAll(l1Delete, l2Delete);
  }

  /**
   * Waits for both futures to complete, wrapping any failure.
   *
   * @param first the first future to wait for
   * @param second the second future to wait for
   * @throws CacheSetException if either future fails or is interrupted
   */
  private static void awaitAll(final Future<?> first, final Future<?> second) {
    try {
      first.get();
      second.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while writing to cache.", e);
      throw new CacheSetException("Interrupted while writing to cache.", e);
    } catch (ExecutionException e) {
      log.error("Failed to write to cache.", e.getCause());
      throw new CacheSetException("Failed to write to cache.", e.getCause());
    }
  }
}
