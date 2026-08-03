package net.sahibnanda.portfolio.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * A simple in-process cache with a per-entry time-to-live, backed by Caffeine.
 */
@Component
public final class InMemoryCache {

  /** Underlying Caffeine cache, with per-entry TTL via {@link Expiry}. */
  private final Cache<String, CacheEntry<Object>> cache = Caffeine.newBuilder()
      .expireAfter(new Expiry<String, CacheEntry<Object>>() {
        @Override
        public long expireAfterCreate(final String key,
            final CacheEntry<Object> value, final long currentTime) {
          return TimeUnit.SECONDS.toNanos(value.ttlSeconds());
        }

        @Override
        public long expireAfterUpdate(final String key,
            final CacheEntry<Object> value, final long currentTime,
            final long currentDuration) {
          return TimeUnit.SECONDS.toNanos(value.ttlSeconds());
        }

        @Override
        public long expireAfterRead(final String key,
            final CacheEntry<Object> value, final long currentTime,
            final long currentDuration) {
          return currentDuration;
        }
      }).build();

  /**
   * A cached value paired with the time-to-live it was stored with.
   *
   * @param <V> the type of the cached value
   * @param value the cached value
   * @param ttlSeconds how long, in seconds, the entry should live
   */
  private record CacheEntry<V>(V value, long ttlSeconds) {
  }

  /**
   * Stores a value under the given key, expiring it after the given
   * time-to-live.
   *
   * @param key the cache key
   * @param value the value to store
   * @param ttlSeconds how long, in seconds, the entry should live
   */
  public void set(final String key, final Object value, final long ttlSeconds) {
    cache.put(key, new CacheEntry<>(value, ttlSeconds));
  }

  /**
   * Retrieves a cached value by key, if present and of the expected type.
   *
   * @param key the cache key
   * @param type the expected type of the cached value
   * @param <T> the expected type of the cached value
   * @return the cached value, or {@code null} if absent, expired, or not an
   *         instance of {@code type}
   */
  public <T> T get(final String key, final Class<T> type) {
    CacheEntry<Object> entry = cache.getIfPresent(key);
    if (entry == null) {
      return null;
    }

    Object value = entry.value();
    return type.isInstance(value) ? type.cast(value) : null;
  }
}
