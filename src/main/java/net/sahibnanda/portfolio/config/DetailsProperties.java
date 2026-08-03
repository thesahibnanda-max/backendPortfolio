package net.sahibnanda.portfolio.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for aggregated portfolio details.
 *
 * @param leetcodeUserNames LeetCode usernames to fetch details for; the first
 *        entry is also used as the primary account for cross-domain identity
 *        enrichment in Profile/Personality Details
 * @param codeforcesUserNames Codeforces handles to fetch details for
 * @param githubUserNames GitHub usernames to fetch details for
 */
@ConfigurationProperties(prefix = "details")
public record DetailsProperties(List<String> leetcodeUserNames,
    List<String> codeforcesUserNames, List<String> githubUserNames) {
}
