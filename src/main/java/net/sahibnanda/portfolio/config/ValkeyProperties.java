package net.sahibnanda.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Valkey cache connection.
 *
 * @param host hostname of the Valkey server
 * @param port port of the Valkey server
 * @param username username for Valkey authentication, or blank if
 *        unauthenticated
 * @param password password for Valkey authentication, or blank if
 *        unauthenticated
 * @param useTls whether to connect over TLS
 */
@ConfigurationProperties(prefix = "valkey")
public record ValkeyProperties(String host, Integer port, String username,
    String password, Boolean useTls) {
}
