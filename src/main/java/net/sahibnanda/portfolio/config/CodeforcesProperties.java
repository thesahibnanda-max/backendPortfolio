package net.sahibnanda.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Codeforces API client.
 *
 * @param baseUrl base URL of the Codeforces API
 */
@ConfigurationProperties(prefix = "codeforces")
public record CodeforcesProperties(String baseUrl) {
}
