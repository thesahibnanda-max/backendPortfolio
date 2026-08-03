package net.sahibnanda.portfolio.services;

import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Enforces fixed-window rate limits backed by {@link ValkeyCache}, shared
 * across application instances.
 */
@Service
public class RateLimitService {

  /** Cache used to track request counts per rate-limit key. */
  private final ValkeyCache cache;

  /**
   * Constructs a new rate limit service.
   *
   * @param valkeyCache the cache used to track request counts
   */
  public RateLimitService(final ValkeyCache valkeyCache) {
    this.cache = Objects.requireNonNull(valkeyCache, "cache must not be null");
  }

  /**
   * Checks whether a request identified by {@code key} is allowed under a
   * fixed-window limit of {@code maxAllowed} requests per {@code
   * ttlSeconds}-second window.
   *
   * @param key the rate-limit key (e.g. an API name, a username, or both
   *        combined)
   * @param maxAllowed the maximum number of requests allowed per window
   * @param ttlSeconds the window's length, in seconds
   * @return {@code true} if the request is within the limit, {@code false} if
   *         it should be rejected
   * @throws IllegalArgumentException if {@code key} is blank
   */
  public boolean isAllowed(final String key, final long maxAllowed,
      final long ttlSeconds) {
    if (StringUtils.isEmpty(key)) {
      throw new IllegalArgumentException("key can't be empty");
    }

    return cache.incrementWithExpire(key, ttlSeconds) <= maxAllowed;
  }
}
