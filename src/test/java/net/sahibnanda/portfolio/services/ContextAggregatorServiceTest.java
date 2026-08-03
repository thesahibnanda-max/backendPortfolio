package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.sahibnanda.portfolio.cache.InMemoryCache;
import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.client.CodeforcesClient;
import net.sahibnanda.portfolio.client.GitHubClient;
import net.sahibnanda.portfolio.client.LeetcodeClient;
import net.sahibnanda.portfolio.client.ProfileClient;
import net.sahibnanda.portfolio.config.CodeforcesProperties;
import net.sahibnanda.portfolio.config.DetailsProperties;
import net.sahibnanda.portfolio.config.GitHubProperties;
import net.sahibnanda.portfolio.config.LeetcodeProperties;
import net.sahibnanda.portfolio.config.ProfileProperties;
import net.sahibnanda.portfolio.config.ValkeyProperties;
import net.sahibnanda.portfolio.enums.ContextType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ContextAggregatorServiceTest {

  private static ContextAggregatorService contextAggregatorService;

  @BeforeAll
  static void setup() {
    DetailsProperties detailsProperties =
        new DetailsProperties(List.of("imsahibnanda"), List.of("shisukenohara"),
            List.of("thesahibnanda-max", "thesahibnanda"));

    LeetcodeClient leetcodeClient =
        new LeetcodeClient(new LeetcodeProperties("https://leetcode.com"));
    CodeforcesClient codeforcesClient = new CodeforcesClient(
        new CodeforcesProperties("https://codeforces.com"));
    GitHubClient gitHubClient =
        new GitHubClient(new GitHubProperties("https://api.github.com"));
    ProfileClient profileClient = new ProfileClient(new ProfileProperties(
        "https://zaiwjonzbotyjmoghqzh.supabase.co/storage/v1/object/"
            + "public/portfolio/profile.json",
        "https://zaiwjonzbotyjmoghqzh.supabase.co/storage/v1/object/"
            + "public/portfolio/personality.json"));
    CacheService cacheService = new CacheService(new InMemoryCache(),
        new ValkeyCache(new ValkeyProperties("localhost", 6379, null, null)));

    DetailsService detailsService =
        new DetailsService(detailsProperties, leetcodeClient, codeforcesClient,
            gitHubClient, profileClient, cacheService);

    contextAggregatorService = new ContextAggregatorService(detailsService);
  }

  @Test
  void aggregateSingleDomainContainsRealData() {
    String aggregated =
        contextAggregatorService.aggregate(List.of(ContextType.LEETCODE));
    System.out.println(aggregated);

    assertThat(aggregated).contains("LEETCODE").contains("imsahibnanda");
  }

  @Test
  void aggregateMultipleDomainsFollowsDeclaredOrderRegardlessOfInputOrder() {
    String aggregated = contextAggregatorService
        .aggregate(List.of(ContextType.GITHUB, ContextType.PROFILE));
    System.out.println(aggregated);

    assertThat(aggregated).contains("PROFILE:").contains("GITHUB (");
    assertThat(aggregated.indexOf("PROFILE:"))
        .isLessThan(aggregated.indexOf("GITHUB ("));
  }

  @Test
  void aggregateReturnsEmptyForNoneOrEmptyOrNull() {
    assertThat(contextAggregatorService.aggregate(List.of(ContextType.NONE)))
        .isEmpty();
    assertThat(contextAggregatorService.aggregate(List.of())).isEmpty();
    assertThat(contextAggregatorService.aggregate(null)).isEmpty();
  }
}
