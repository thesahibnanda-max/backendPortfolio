package net.sahibnanda.portfolio.cache;

import glide.api.GlideClient;
import glide.api.models.commands.SetOptions;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ServerCredentials;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.config.ValkeyProperties;
import net.sahibnanda.portfolio.exception.ValkeyCacheException;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Component;

/**
 * A Valkey-backed cache, used for data that should be shared across application
 * instances (unlike {@link InMemoryCache}, which is per-instance).
 */
@Slf4j
@Component
public final class ValkeyCache {

  /** Underlying Valkey GLIDE client used for all cache operations. */
  private final GlideClient glideClient;

  /**
   * Creates a cache connected to the configured Valkey server.
   *
   * @param valkeyProperties Valkey configuration; must not be {@code null}
   * @throws NullPointerException if {@code valkeyProperties} is {@code null}
   * @throws ValkeyCacheException if the connection cannot be established
   */
  public ValkeyCache(final ValkeyProperties valkeyProperties) {

    Objects.requireNonNull(valkeyProperties,
        "valkeyProperties must not be null");

    NodeAddress address = NodeAddress.builder().host(valkeyProperties.host())
        .port(valkeyProperties.port()).build();

    // lazyConnect defers GlideClient's own automatic connection so we
    // can validate it ourselves via an explicit ping() right below,
    // rather than relying on createClient()'s implicit connect. Either
    // way, a bad connection fails synchronously here in the
    // constructor, so an unreachable Valkey server fails application
    // startup rather than surfacing only on first use.
    GlideClientConfiguration config = GlideClientConfiguration.builder()
        .address(address).credentials(buildCredentials(valkeyProperties))
        .lazyConnect(true).build();

    this.glideClient = await(GlideClient.createClient(config));
    await(this.glideClient.ping());
    log.info("Connected to Valkey at {}:{}", valkeyProperties.host(),
        valkeyProperties.port());
  }

  /**
   * Builds Valkey auth credentials from the given properties, if either is
   * configured.
   *
   * @param valkeyProperties Valkey configuration
   * @return the credentials to authenticate with, or {@code null} if neither a
   *         username nor a password is configured
   */
  private static ServerCredentials buildCredentials(
      final ValkeyProperties valkeyProperties) {
    if (StringUtils.isEmpty(valkeyProperties.username())
        && StringUtils.isEmpty(valkeyProperties.password())) {
      return null;
    }

    return ServerCredentials.builder().username(valkeyProperties.username())
        .password(valkeyProperties.password()).build();
  }

  /**
   * Stores a value under the given key, expiring it after the given
   * time-to-live.
   *
   * @param key the cache key
   * @param value the value to store
   * @param ttlSeconds how long, in seconds, the entry should live
   * @throws ValkeyCacheException if the write fails
   */
  public void set(final String key, final String value, final long ttlSeconds) {
    SetOptions options = SetOptions.builder()
        .expiry(SetOptions.Expiry.Seconds(ttlSeconds)).build();

    await(glideClient.set(key, value, options));
  }

  /**
   * Retrieves a cached value by key.
   *
   * @param key the cache key
   * @return the cached value, or {@code null} if absent or expired
   * @throws ValkeyCacheException if the read fails
   */
  public String get(final String key) {
    return await(glideClient.get(key));
  }

  /**
   * Deletes a cached value by key.
   *
   * @param key the cache key
   * @throws ValkeyCacheException if the deletion fails
   */
  public void delete(final String key) {
    await(glideClient.del(new String[] {key}));
  }

  /**
   * Returns the remaining time-to-live for a key.
   *
   * @param key the cache key
   * @return remaining seconds, {@code -1} if the key has no expiry, or
   *         {@code -2} if the key does not exist
   * @throws ValkeyCacheException if the read fails
   */
  public Long ttl(final String key) {
    return await(glideClient.ttl(key));
  }

  /**
   * Blocks on a GLIDE command future, translating its checked exceptions into
   * {@link ValkeyCacheException}.
   *
   * @param future the in-flight command result to wait for
   * @param <T> the command's result type
   * @return the command's result
   * @throws ValkeyCacheException if the command fails or is interrupted
   */
  private static <T> T await(final CompletableFuture<T> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while calling Valkey.", e);
      throw new ValkeyCacheException("Interrupted while calling Valkey.", e);
    } catch (ExecutionException e) {
      log.error("Failed to call Valkey.", e.getCause());
      throw new ValkeyCacheException("Failed to call Valkey.", e.getCause());
    }
  }
}
