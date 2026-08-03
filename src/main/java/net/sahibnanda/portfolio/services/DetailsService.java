package net.sahibnanda.portfolio.services;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.client.CodeforcesClient;
import net.sahibnanda.portfolio.client.GitHubClient;
import net.sahibnanda.portfolio.client.LeetcodeClient;
import net.sahibnanda.portfolio.client.ProfileClient;
import net.sahibnanda.portfolio.config.DetailsProperties;
import net.sahibnanda.portfolio.models.CodeforcesUserRatingResponse;
import net.sahibnanda.portfolio.models.GitHubRepository;
import net.sahibnanda.portfolio.models.GitHubUser;
import net.sahibnanda.portfolio.models.LeetcodeUserProfileRequest;
import net.sahibnanda.portfolio.models.LeetcodeUserProfileResponse;
import net.sahibnanda.portfolio.models.PersonalityResponse;
import net.sahibnanda.portfolio.models.ProfileResponse;
import net.sahibnanda.portfolio.objects.CodeforcesDetails;
import net.sahibnanda.portfolio.objects.GitHubDetails;
import net.sahibnanda.portfolio.objects.LeetcodeDetails;
import net.sahibnanda.portfolio.objects.PersonalityDetails;
import net.sahibnanda.portfolio.objects.ProfileDetails;
import net.sahibnanda.portfolio.utils.JsonUtils;
import org.springframework.stereotype.Service;

/**
 * Composes {@link LeetcodeClient}, {@link CodeforcesClient},
 * {@link GitHubClient} and {@link ProfileClient} into reader-friendly details
 * objects, cache-aside through {@link CacheService}. Wherever a call needs more
 * than one API call, those calls run concurrently on virtual threads; if any
 * one of them fails, the whole call fails (no partial results).
 */
@Slf4j
@Service
public final class DetailsService {

  /** Time-to-live, in seconds, for the local (L1) tier of a cached response. */
  private static final long CACHE_TTL_SECONDS_L1 = 600;

  /**
   * Time-to-live, in seconds, for the shared (L2) tier of a cached response.
   */
  private static final long CACHE_TTL_SECONDS_L2 = 1200;

  /** Cache key prefix for a raw LeetCode profile response. */
  private static final String CACHE_KEY_LEETCODE_RAW_PREFIX = "leetcode:raw:";

  /** Cache key prefix for a raw Codeforces rating response. */
  private static final String CACHE_KEY_CODEFORCES_RAW_PREFIX =
      "codeforces:raw:";

  /** Cache key prefix for a raw GitHub user response. */
  private static final String CACHE_KEY_GITHUB_USER_PREFIX = "github:user:";

  /** Cache key prefix for a raw GitHub repositories response. */
  private static final String CACHE_KEY_GITHUB_REPOS_PREFIX = "github:repos:";

  /** Cache key for the raw hosted profile JSON. */
  private static final String CACHE_KEY_PROFILE_RAW = "profile:raw";

  /** Cache key for the raw hosted personality JSON. */
  private static final String CACHE_KEY_PERSONALITY_RAW = "personality:raw";

  /** LeetCode's difficulty label for the combined solved count. */
  private static final String DIFFICULTY_ALL = "All";

  /** LeetCode's difficulty label for easy problems. */
  private static final String DIFFICULTY_EASY = "Easy";

  /** LeetCode's difficulty label for medium problems. */
  private static final String DIFFICULTY_MEDIUM = "Medium";

  /** LeetCode's difficulty label for hard problems. */
  private static final String DIFFICULTY_HARD = "Hard";

  /** Message logged when a details fetch is interrupted while awaiting. */
  private static final String ERROR_INTERRUPTED =
      "Interrupted while fetching details.";

  /** Message logged when a details fetch fails for a non-runtime cause. */
  private static final String ERROR_FETCH_FAILED = "Failed to fetch details.";

  /** Message logged when a raw response fails to cache. */
  private static final String ERROR_CACHE_FAILED =
      "Failed to cache details for key {}";

  /** Configured usernames per platform. */
  private final DetailsProperties properties;

  /** Client used to fetch LeetCode profile data. */
  private final LeetcodeClient leetcode;

  /** Client used to fetch Codeforces rating data. */
  private final CodeforcesClient codeforces;

  /** Client used to fetch GitHub profile/repository data. */
  private final GitHubClient gitHub;

  /** Client used to fetch the hosted profile/personality JSON. */
  private final ProfileClient profile;

  /** Cache checked before, and populated after, every raw fetch. */
  private final CacheService cache;

  /** Executor used to run concurrent API calls and cache writes. */
  private final ExecutorService executor =
      Executors.newVirtualThreadPerTaskExecutor();

  /**
   * In-flight loads, keyed by cache key, so concurrent misses for the same key
   * share one upstream call instead of each triggering its own.
   */
  private final ConcurrentHashMap<String, CompletableFuture<String>> inFlight =
      new ConcurrentHashMap<>();

  /**
   * Creates a new details service.
   *
   * @param detailsProperties configured usernames per platform
   * @param leetcodeClient client used to fetch LeetCode profile data
   * @param codeforcesClient client used to fetch Codeforces rating data
   * @param gitHubClient client used to fetch GitHub profile/repository data
   * @param profileClient client used to fetch the hosted profile/ personality
   *        JSON
   * @param cacheService cache checked before, and populated after, every raw
   *        fetch
   */
  public DetailsService(final DetailsProperties detailsProperties,
      final LeetcodeClient leetcodeClient,
      final CodeforcesClient codeforcesClient, final GitHubClient gitHubClient,
      final ProfileClient profileClient, final CacheService cacheService) {
    this.properties = detailsProperties;
    this.leetcode = leetcodeClient;
    this.codeforces = codeforcesClient;
    this.gitHub = gitHubClient;
    this.profile = profileClient;
    this.cache = cacheService;
  }

  /**
   * Fetches LeetCode-only stats for every configured LeetCode username.
   *
   * @return one entry per configured username
   */
  public List<LeetcodeDetails> getLeetcodeDetails() {
    return runAllParallel(properties.leetcodeUserNames(),
        username -> mapLeetcodeDetails(fetchRawLeetcodeProfile(username)));
  }

  /**
   * Fetches Codeforces-only stats for every configured Codeforces handle.
   *
   * @return one entry per configured handle
   */
  public List<CodeforcesDetails> getCodeforcesDetails() {
    return runAllParallel(properties.codeforcesUserNames(),
        handle -> mapCodeforcesDetails(handle,
            fetchRawCodeforcesRating(handle)));
  }

  /**
   * Fetches GitHub-only profile and repository stats for every configured
   * GitHub username.
   *
   * @return one entry per configured username
   */
  public List<GitHubDetails> getGithubDetails() {
    return runAllParallel(properties.githubUserNames(),
        this::fetchGithubDetails);
  }

  /**
   * Fetches the portfolio owner's profile, enriched with the tracked platform
   * handles and the identity fields LeetCode records that
   * {@link ProfileResponse} doesn't already cover.
   *
   * @return the composed profile details
   */
  public ProfileDetails getProfileDetails() {
    Future<ProfileResponse> profileFuture =
        executor.submit(this::fetchRawProfile);
    Future<LeetcodeUserProfileResponse> leetcodeFuture = executor
        .submit(() -> fetchRawLeetcodeProfile(primaryLeetcodeUsername()));

    return mapProfileDetails(await(profileFuture), await(leetcodeFuture));
  }

  /**
   * Returns just the portfolio owner's name, without the rest of
   * {@link #getProfileDetails()}'s bundled profile/LeetCode data.
   *
   * @return the portfolio owner's name, or an empty string if unavailable
   */
  public String getPortfolioOwnerName() {
    ProfileResponse.ProfileDetails identity =
        getProfileDetails().getProfileDetails();
    return identity == null || identity.getName() == null ? ""
        : identity.getName();
  }

  /**
   * Fetches the portfolio owner's personality profile, enriched with the
   * biography LeetCode records.
   *
   * @return the composed personality details
   */
  public PersonalityDetails getPersonalityDetails() {
    Future<PersonalityResponse> personalityFuture =
        executor.submit(this::fetchRawPersonality);
    Future<LeetcodeUserProfileResponse> leetcodeFuture = executor
        .submit(() -> fetchRawLeetcodeProfile(primaryLeetcodeUsername()));

    return mapPersonalityDetails(await(personalityFuture),
        await(leetcodeFuture));
  }

  private GitHubDetails fetchGithubDetails(final String username) {
    Future<GitHubUser> userFuture =
        executor.submit(() -> fetchRawGithubUser(username));
    Future<List<GitHubRepository>> reposFuture =
        executor.submit(() -> fetchRawGithubRepos(username));

    return mapGithubDetails(await(userFuture), await(reposFuture));
  }

  private String primaryLeetcodeUsername() {
    return properties.leetcodeUserNames().getFirst();
  }

  private LeetcodeUserProfileResponse fetchRawLeetcodeProfile(
      final String username) {
    return getCachedRaw(CACHE_KEY_LEETCODE_RAW_PREFIX + username,
        LeetcodeUserProfileResponse.class, () -> leetcode.getUserProfileDetails(
            LeetcodeUserProfileRequest.builder(username).everything().build()));
  }

  private CodeforcesUserRatingResponse fetchRawCodeforcesRating(
      final String handle) {
    return getCachedRaw(CACHE_KEY_CODEFORCES_RAW_PREFIX + handle,
        CodeforcesUserRatingResponse.class,
        () -> codeforces.getUserRatingDetails(handle));
  }

  private GitHubUser fetchRawGithubUser(final String username) {
    return getCachedRaw(CACHE_KEY_GITHUB_USER_PREFIX + username,
        GitHubUser.class, () -> gitHub.getUserDetails(username));
  }

  private List<GitHubRepository> fetchRawGithubRepos(final String username) {
    return getCachedRawList(CACHE_KEY_GITHUB_REPOS_PREFIX + username,
        new TypeReference<List<GitHubRepository>>() {
        }, () -> gitHub.listUserRepositories(username, 1));
  }

  private ProfileResponse fetchRawProfile() {
    return getCachedRaw(CACHE_KEY_PROFILE_RAW, ProfileResponse.class,
        profile::getProfile);
  }

  private PersonalityResponse fetchRawPersonality() {
    return getCachedRaw(CACHE_KEY_PERSONALITY_RAW, PersonalityResponse.class,
        profile::getPersonality);
  }

  private static LeetcodeDetails mapLeetcodeDetails(
      final LeetcodeUserProfileResponse raw) {
    LeetcodeUserProfileResponse.MatchedUser matchedUser =
        raw.getData().getMatchedUser();
    LeetcodeUserProfileResponse.Profile leetcodeProfile =
        matchedUser.getProfile();
    List<LeetcodeUserProfileResponse.AcSubmissionNum> acSubmissionNum =
        matchedUser.getSubmitStatsGlobal().getAcSubmissionNum();
    LeetcodeUserProfileResponse.UserContestRanking contestRanking =
        raw.getData().getUserContestRanking();
    LeetcodeUserProfileResponse.UserCalendar calendar =
        matchedUser.getUserCalendar();
    LeetcodeUserProfileResponse.TagProblemCounts tagCounts =
        matchedUser.getTagProblemCounts();

    return LeetcodeDetails.builder().username(matchedUser.getUsername())
        .ranking(leetcodeProfile.getRanking())
        .reputation(leetcodeProfile.getReputation())
        .totalSolved(findSolvedCount(acSubmissionNum, DIFFICULTY_ALL))
        .easySolved(findSolvedCount(acSubmissionNum, DIFFICULTY_EASY))
        .mediumSolved(findSolvedCount(acSubmissionNum, DIFFICULTY_MEDIUM))
        .hardSolved(findSolvedCount(acSubmissionNum, DIFFICULTY_HARD))
        .badges(mapBadges(matchedUser.getBadges()))
        .languageProblemsSolved(
            mapLanguageProblemsSolved(matchedUser.getLanguageProblemCount()))
        .advancedTagsSolved(
            mapTagsSolved(tagCounts == null ? null : tagCounts.getAdvanced()))
        .intermediateTagsSolved(mapTagsSolved(
            tagCounts == null ? null : tagCounts.getIntermediate()))
        .fundamentalTagsSolved(mapTagsSolved(
            tagCounts == null ? null : tagCounts.getFundamental()))
        .contestRating(
            contestRanking == null ? null : contestRanking.getRating())
        .contestGlobalRanking(
            contestRanking == null ? null : contestRanking.getGlobalRanking())
        .currentStreak(calendar == null ? null : calendar.getStreak())
        .totalActiveDays(
            calendar == null ? null : calendar.getTotalActiveDays())
        .build();
  }

  private static Integer findSolvedCount(
      final List<LeetcodeUserProfileResponse.AcSubmissionNum> submissions,
      final String difficulty) {
    if (submissions == null) {
      return null;
    }
    return submissions.stream()
        .filter(entry -> difficulty.equals(entry.getDifficulty()))
        .map(LeetcodeUserProfileResponse.AcSubmissionNum::getCount).findFirst()
        .orElse(null);
  }

  private static List<String> mapBadges(
      final List<LeetcodeUserProfileResponse.Badge> badges) {
    if (badges == null) {
      return List.of();
    }
    return badges.stream()
        .map(LeetcodeUserProfileResponse.Badge::getDisplayName).toList();
  }

  private static Map<String, Integer> mapLanguageProblemsSolved(
      final List<LeetcodeUserProfileResponse.LanguageProblemCount> counts) {
    if (counts == null) {
      return Map.of();
    }
    return counts.stream().collect(Collectors.toMap(
        LeetcodeUserProfileResponse.LanguageProblemCount::getLanguageName,
        LeetcodeUserProfileResponse.LanguageProblemCount::getProblemsSolved));
  }

  private static Map<String, Integer> mapTagsSolved(
      final List<LeetcodeUserProfileResponse.TagProblemCount> tags) {
    if (tags == null) {
      return Map.of();
    }
    return tags.stream()
        .collect(Collectors.toMap(
            LeetcodeUserProfileResponse.TagProblemCount::getTagName,
            LeetcodeUserProfileResponse.TagProblemCount::getProblemsSolved));
  }

  private static CodeforcesDetails mapCodeforcesDetails(final String handle,
      final CodeforcesUserRatingResponse raw) {
    List<CodeforcesUserRatingResponse.RatingChange> history = raw.getResult();
    if (history == null || history.isEmpty()) {
      return CodeforcesDetails.builder().handle(handle).contestsCount(0)
          .ratingHistory(List.of()).build();
    }

    List<CodeforcesDetails.RatingTransition> ratingHistory =
        history.stream().map(DetailsService::mapRatingTransition).toList();

    return CodeforcesDetails.builder().handle(handle)
        .currentRating(history.getLast().getNewRating())
        .maxRating(history.stream()
            .mapToInt(CodeforcesUserRatingResponse.RatingChange::getNewRating)
            .max().orElse(0))
        .contestsCount(history.size()).ratingHistory(ratingHistory).build();
  }

  private static CodeforcesDetails.RatingTransition mapRatingTransition(
      final CodeforcesUserRatingResponse.RatingChange change) {
    return CodeforcesDetails.RatingTransition.builder()
        .contestName(change.getContestName()).rank(change.getRank())
        .oldRating(change.getOldRating()).newRating(change.getNewRating())
        .contestTime(Instant.ofEpochSecond(change.getRatingUpdateTimeSeconds()))
        .build();
  }

  private static GitHubDetails mapGithubDetails(final GitHubUser user,
      final List<GitHubRepository> repositories) {
    List<GitHubDetails.RepositorySummary> summaries =
        repositories == null ? List.of()
            : repositories.stream().map(DetailsService::mapRepositorySummary)
                .toList();

    return GitHubDetails.builder().username(user.getLogin())
        .name(user.getName()).avatarUrl(user.getAvatarUrl()).bio(user.getBio())
        .publicRepos(user.getPublicRepos()).followers(user.getFollowers())
        .following(user.getFollowing()).htmlUrl(user.getHtmlUrl())
        .repositories(summaries).build();
  }

  private static GitHubDetails.RepositorySummary mapRepositorySummary(
      final GitHubRepository repository) {
    return GitHubDetails.RepositorySummary.builder().name(repository.getName())
        .description(repository.getDescription())
        .htmlUrl(repository.getHtmlUrl()).language(repository.getLanguage())
        .stars(repository.getStargazersCount())
        .forks(repository.getForksCount()).updatedAt(repository.getUpdatedAt())
        .build();
  }

  private ProfileDetails mapProfileDetails(final ProfileResponse rawProfile,
      final LeetcodeUserProfileResponse leetcodeProfile) {
    LeetcodeUserProfileResponse.MatchedUser matchedUser =
        leetcodeProfile.getData().getMatchedUser();
    LeetcodeUserProfileResponse.Profile identity = matchedUser.getProfile();

    return ProfileDetails.builder()
        .profileDetails(rawProfile.getProfileDetails())
        .projects(rawProfile.getProjects()).languages(rawProfile.getLanguages())
        .achievements(rawProfile.getAchievements())
        .experience(rawProfile.getExperience())
        .education(rawProfile.getEducation())
        .skillsByCategory(rawProfile.getSkillsByCategory())
        .leetcodeUsernames(properties.leetcodeUserNames())
        .codeforcesUsernames(properties.codeforcesUserNames())
        .githubUsernames(properties.githubUserNames())
        .linkedinUrl(matchedUser.getLinkedinUrl())
        .twitterUrl(matchedUser.getTwitterUrl())
        .websites(identity.getWebsites()).countryName(identity.getCountryName())
        .build();
  }

  private static PersonalityDetails mapPersonalityDetails(
      final PersonalityResponse rawPersonality,
      final LeetcodeUserProfileResponse leetcodeProfile) {
    return PersonalityDetails.builder()
        .personalProfile(rawPersonality.getPersonalProfile())
        .aboutMe(leetcodeProfile.getData().getMatchedUser().getProfile()
            .getAboutMe())
        .build();
  }

  private <T> List<T> runAllParallel(final List<String> usernames,
      final Function<String, T> fetcher) {
    if (usernames == null || usernames.isEmpty()) {
      return List.of();
    }
    List<Future<T>> futures = usernames.stream()
        .map(username -> executor.submit(() -> fetcher.apply(username)))
        .toList();
    return awaitAll(futures);
  }

  private static <T> List<T> awaitAll(final List<Future<T>> futures) {
    List<T> results = new ArrayList<>(futures.size());
    for (Future<T> future : futures) {
      results.add(await(future));
    }
    return results;
  }

  private static <T> T await(final Future<T> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ERROR_INTERRUPTED, e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(ERROR_FETCH_FAILED, e.getCause());
    }
  }

  private <T> T getCachedRaw(final String key, final Class<T> type,
      final Supplier<T> loader) {
    String cachedJson = cache.get(key);
    if (cachedJson != null) {
      return JsonUtils.fromJson(cachedJson, type);
    }

    return JsonUtils.fromJson(coalesce(key, loader), type);
  }

  private <T> List<T> getCachedRawList(final String key,
      final TypeReference<List<T>> type, final Supplier<List<T>> loader) {
    String cachedJson = cache.get(key);
    if (cachedJson != null) {
      return JsonUtils.fromJson(cachedJson, type);
    }

    return JsonUtils.fromJson(coalesce(key, loader), type);
  }

  // Package-private (not private) so DetailsServiceTest can exercise
  // this directly with a synthetic loader -- real upstream calls can't
  // be raced deterministically in a test.
  <T> String coalesce(final String key, final Supplier<T> loader) {
    CompletableFuture<String> future =
        inFlight.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
          T value = loader.get();
          cacheAsync(k, value);
          return JsonUtils.toJson(value);
        }, executor).whenComplete((json, error) -> inFlight.remove(k)));

    try {
      return future.join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw e;
    }
  }

  private void cacheAsync(final String key, final Object value) {
    executor.submit(() -> {
      try {
        cache.set(key, JsonUtils.toJson(value), CACHE_TTL_SECONDS_L1,
            CACHE_TTL_SECONDS_L2);
      } catch (RuntimeException e) {
        log.error(ERROR_CACHE_FAILED, key, e);
      }
    });
  }
}
