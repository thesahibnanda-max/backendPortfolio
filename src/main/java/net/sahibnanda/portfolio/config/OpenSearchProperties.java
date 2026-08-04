package net.sahibnanda.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for connecting to the OpenSearch cluster.
 *
 * @param host hostname of the OpenSearch cluster
 * @param port port of the OpenSearch cluster
 * @param username username for OpenSearch authentication
 * @param password password for OpenSearch authentication
 * @param https whether to connect over HTTPS
 */
@ConfigurationProperties(prefix = "opensearch")
public record OpenSearchProperties(String host, Integer port, String username,
    String password, Boolean https) {
}
