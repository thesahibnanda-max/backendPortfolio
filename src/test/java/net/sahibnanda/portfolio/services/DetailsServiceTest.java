package net.sahibnanda.portfolio.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
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
import net.sahibnanda.portfolio.objects.CodeforcesDetails;
import net.sahibnanda.portfolio.objects.GitHubDetails;
import net.sahibnanda.portfolio.objects.LeetcodeDetails;
import net.sahibnanda.portfolio.objects.PersonalityDetails;
import net.sahibnanda.portfolio.objects.ProfileDetails;
import net.sahibnanda.portfolio.utils.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DetailsServiceTest {

  private static DetailsService detailsService;

  @BeforeAll
  static void setup() {
    DetailsProperties detailsProperties =
        new DetailsProperties(List.of("imsahibnanda"), List.of("shisukenohara"),
            List.of("thesahibnanda-max", "thesahibnanda"));

    LeetcodeClient leetcodeClient = new LeetcodeClient(
        new LeetcodeProperties(TestEnvironment.LEETCODE_BASE_URL));
    CodeforcesClient codeforcesClient = new CodeforcesClient(
        new CodeforcesProperties(TestEnvironment.CODEFORCES_BASE_URL));
    GitHubClient gitHubClient =
        new GitHubClient(new GitHubProperties(TestEnvironment.GITHUB_BASE_URL));
    ProfileClient profileClient = new ProfileClient(
        new ProfileProperties(TestEnvironment.PROFILE_JSON_RETRIEVAL_URL,
            TestEnvironment.PERSONALITY_JSON_RETRIEVAL_URL));
    CacheService cacheService = new CacheService(new InMemoryCache(),
        new ValkeyCache(new ValkeyProperties(TestEnvironment.VALKEY_HOST,
            TestEnvironment.VALKEY_PORT, null, null)));

    detailsService = new DetailsService(detailsProperties, leetcodeClient,
        codeforcesClient, gitHubClient, profileClient, cacheService);
  }

  @Test
  void getLeetcodeDetails() {
    List<LeetcodeDetails> details = detailsService.getLeetcodeDetails();
    System.out.println(details);

    assertNotNull(details);
    assertEquals(1, details.size());

    LeetcodeDetails imsahibnanda = details.get(0);
    assertEquals("imsahibnanda", imsahibnanda.getUsername());
    assertTrue(imsahibnanda.getRanking() > 0);
    assertTrue(imsahibnanda.getTotalSolved() > 0);
    assertFalse(imsahibnanda.getBadges().isEmpty());
    assertFalse(imsahibnanda.getLanguageProblemsSolved().isEmpty());
  }

  @Test
  void getCodeforcesDetails() {
    List<CodeforcesDetails> details = detailsService.getCodeforcesDetails();
    System.out.println(details);

    assertNotNull(details);
    assertEquals(1, details.size());

    CodeforcesDetails shisukenohara = details.get(0);
    assertEquals("shisukenohara", shisukenohara.getHandle());
    assertTrue(shisukenohara.getContestsCount() > 0);
    assertFalse(shisukenohara.getRatingHistory().isEmpty());
  }

  @Test
  void getGithubDetails() {
    List<GitHubDetails> details = detailsService.getGithubDetails();
    System.out.println(details);

    assertNotNull(details);
    assertEquals(2, details.size());

    List<String> usernames =
        details.stream().map(GitHubDetails::getUsername).toList();
    assertTrue(usernames.contains("thesahibnanda-max"));
    assertTrue(usernames.contains("thesahibnanda"));
  }

  @Test
  void getProfileDetails() {
    ProfileDetails details = detailsService.getProfileDetails();
    System.out.println(details);

    assertNotNull(details);
    assertEquals("Sahib Nanda", details.getProfileDetails().getName());
    assertEquals(List.of("imsahibnanda"), details.getLeetcodeUsernames());
    assertEquals(List.of("shisukenohara"), details.getCodeforcesUsernames());
    assertEquals(List.of("thesahibnanda-max", "thesahibnanda"),
        details.getGithubUsernames());
    assertNotNull(details.getLinkedinUrl());
  }

  @Test
  void getPersonalityDetails() {
    PersonalityDetails details = detailsService.getPersonalityDetails();
    System.out.println(details);

    assertNotNull(details);
    assertNotNull(details.getPersonalProfile());
    assertNotNull(details.getAboutMe());
    assertFalse(details.getAboutMe().isBlank());
  }

  @Test
  void coalescesConcurrentMissesForSameKey()
      throws InterruptedException, ExecutionException {
    String key = "test:coalesce:" + UUID.randomUUID();
    AtomicInteger loadCount = new AtomicInteger();
    int callers = 20;
    CountDownLatch ready = new CountDownLatch(callers);
    CountDownLatch start = new CountDownLatch(1);

    ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    List<Future<String>> futures = new ArrayList<>();
    for (int i = 0; i < callers; i++) {
      futures.add(pool.submit(() -> {
        ready.countDown();
        start.await();
        return detailsService.coalesce(key, () -> {
          loadCount.incrementAndGet();
          try {
            Thread.sleep(150);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return "loaded-value";
        });
      }));
    }
    ready.await();
    start.countDown();

    for (Future<String> future : futures) {
      assertEquals("\"loaded-value\"", future.get());
    }
    assertEquals(1, loadCount.get());
  }

  @Test
  void propagatesLoaderFailureToAllCoalescedCallers()
      throws InterruptedException {
    String key = "test:coalesce-fail:" + UUID.randomUUID();
    AtomicInteger loadCount = new AtomicInteger();
    int callers = 10;
    CountDownLatch ready = new CountDownLatch(callers);
    CountDownLatch start = new CountDownLatch(1);

    ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    List<Future<String>> futures = new ArrayList<>();
    for (int i = 0; i < callers; i++) {
      futures.add(pool.submit(() -> {
        ready.countDown();
        start.await();
        return detailsService.coalesce(key, () -> {
          loadCount.incrementAndGet();
          try {
            Thread.sleep(150);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          throw new IllegalStateException("synthetic failure");
        });
      }));
    }
    ready.await();
    start.countDown();

    for (Future<String> future : futures) {
      ExecutionException e =
          assertThrows(ExecutionException.class, future::get);
      assertInstanceOf(IllegalStateException.class, e.getCause());
      assertEquals("synthetic failure", e.getCause().getMessage());
    }
    assertEquals(1, loadCount.get());
  }
}
