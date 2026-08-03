package net.sahibnanda.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Profile JSON client.
 *
 * @param profileJSONRetreivalURL full URL of the hosted profile JSON
 * @param personalityJSONRetreivalURL full URL of the hosted personality JSON
 */
@ConfigurationProperties(prefix = "profile")
public record ProfileProperties(String profileJSONRetreivalURL,
    String personalityJSONRetreivalURL) {
}
