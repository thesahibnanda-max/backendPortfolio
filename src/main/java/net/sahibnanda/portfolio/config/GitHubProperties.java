package net.sahibnanda.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the GitHub API client.
 *
 * @param baseUrl base URL of the GitHub API
 */
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(String baseUrl) {
}
