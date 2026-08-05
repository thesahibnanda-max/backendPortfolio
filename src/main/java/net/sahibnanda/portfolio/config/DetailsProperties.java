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
 * @param resumeLink URL of the portfolio owner's resume
 * @param profilePhotoLinks URLs of the portfolio owner's profile photo(s)
 * @param leetcodeProfileUrlFormat {@link String#format} pattern taking the
 *        LeetCode base URL and a username, used to build a public LeetCode
 *        profile link
 * @param codeforcesProfileUrlFormat {@link String#format} pattern taking the
 *        Codeforces base URL and a handle, used to build a public Codeforces
 *        profile link
 */
@ConfigurationProperties(prefix = "details")
public record DetailsProperties(List<String> leetcodeUserNames,
    List<String> codeforcesUserNames, List<String> githubUserNames,
    String resumeLink, List<String> profilePhotoLinks,
    String leetcodeProfileUrlFormat, String codeforcesProfileUrlFormat) {
}
