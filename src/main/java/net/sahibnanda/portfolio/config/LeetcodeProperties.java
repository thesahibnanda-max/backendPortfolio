package net.sahibnanda.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the LeetCode API client.
 *
 * @param baseUrl base URL of the LeetCode API
 */
@ConfigurationProperties(prefix = "leetcode")
public record LeetcodeProperties(String baseUrl) {
}
