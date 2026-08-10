package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.sahibnanda.portfolio.cache.AbstractValkeyIntegrationTest;
import net.sahibnanda.portfolio.cache.InMemoryCache;
import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.client.CodeforcesClient;
import net.sahibnanda.portfolio.client.GitHubClient;
import net.sahibnanda.portfolio.client.GroqClient;
import net.sahibnanda.portfolio.client.LeetcodeClient;
import net.sahibnanda.portfolio.client.ProfileClient;
import net.sahibnanda.portfolio.config.CodeforcesProperties;
import net.sahibnanda.portfolio.config.DetailsProperties;
import net.sahibnanda.portfolio.config.GitHubProperties;
import net.sahibnanda.portfolio.config.GroqProperties;
import net.sahibnanda.portfolio.config.LLMProperties;
import net.sahibnanda.portfolio.config.LeetcodeProperties;
import net.sahibnanda.portfolio.config.ProfileProperties;
import net.sahibnanda.portfolio.config.ValkeyProperties;
import net.sahibnanda.portfolio.enums.ContextType;
import net.sahibnanda.portfolio.enums.GroqModel;
import net.sahibnanda.portfolio.templates.PromptTemplates;
import net.sahibnanda.portfolio.utils.EnvironmentUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import net.sahibnanda.portfolio.utils.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Live Groq streaming check against a real key, exercised through the full
 * Worker AI pipeline. Disabled by default.
 *
 * <p>
 * To run: export the {@code GROQ_API_KEYS} value from this project's
 * {@code stage.env} into your shell before running Maven, e.g. (bash):
 * 
 * <pre>{@code
 * export GROQ_API_KEYS=$(grep '^GROQ_API_KEYS=' stage.env | cut -d= -f2-)
 * mvn -Dtest=WorkerServiceStreamIntegrationTest test
 * }</pre>
 * 
 * then temporarily remove the {@code @Disabled} annotation. Never commit
 * {@code stage.env} or export its contents in CI.
 */
@Disabled("Manual-only: requires a real GROQ_API_KEYS (stage) exported in the shell.")
class WorkerServiceStreamIntegrationTest extends AbstractValkeyIntegrationTest {

  private static WorkerService workerService;

  @BeforeAll
  static void setup() {
    EnvironmentUtils.loadDotenv();
    String apiKeys = EnvironmentUtils.get("GROQ_API_KEYS");
    assumeTrue(!StringUtils.isEmpty(apiKeys),
        "GROQ_API_KEYS not configured; skipping live Groq API test.");

    GroqClient groqClient =
        new GroqClient(new GroqProperties(TestEnvironment.GROQ_BASE_URL,
            List.of(apiKeys.split(","))));
    LLMProperties llmProperties = new LLMProperties(
        Map.of(GroqModel.LLAMA_3_3_70B, 50, GroqModel.GPT_OSS_20B, 20,
            GroqModel.QWEN_3_6_27B, 20, GroqModel.GPT_OSS_120B, 10),
        0.8, 1.4, 0.9, 1.0, 2048);
    LLMService llmService = new LLMService(groqClient, llmProperties);

    DetailsProperties detailsProperties =
        new DetailsProperties(List.of("imsahibnanda"), List.of("shisukenohara"),
            List.of("thesahibnanda-max", "thesahibnanda"),
            "https://example.com/resume.pdf",
            List.of("https://example.com/photo.png"),
            TestEnvironment.DETAILS_LEETCODE_PROFILE_URL_FORMAT,
            TestEnvironment.DETAILS_CODEFORCES_PROFILE_URL_FORMAT);
    LeetcodeProperties leetcodeProperties =
        new LeetcodeProperties(TestEnvironment.LEETCODE_BASE_URL);
    CodeforcesProperties codeforcesProperties =
        new CodeforcesProperties(TestEnvironment.CODEFORCES_BASE_URL);
    LeetcodeClient leetcodeClient = new LeetcodeClient(leetcodeProperties);
    CodeforcesClient codeforcesClient =
        new CodeforcesClient(codeforcesProperties);
    GitHubClient gitHubClient =
        new GitHubClient(new GitHubProperties(TestEnvironment.GITHUB_BASE_URL));
    ProfileClient profileClient = new ProfileClient(
        new ProfileProperties(TestEnvironment.PROFILE_JSON_RETRIEVAL_URL,
            TestEnvironment.PERSONALITY_JSON_RETRIEVAL_URL));
    CacheService cacheService = new CacheService(new InMemoryCache(),
        new ValkeyCache(new ValkeyProperties(VALKEY.getHost(),
            VALKEY.getMappedPort(6379), null, null, false)));
    DetailsService detailsService =
        new DetailsService(detailsProperties, leetcodeClient, codeforcesClient,
            gitHubClient, profileClient, cacheService);
    ContextAggregatorService contextAggregatorService =
        new ContextAggregatorService(detailsService);

    workerService = new WorkerService(llmService, new PromptTemplates(),
        contextAggregatorService, detailsService);
  }

  @Test
  void respondStreamWithNoContextReturnsAnswerMatchingStreamedChunks() {
    List<String> chunks = new ArrayList<>();
    String answer = workerService.respondStream(List.of(ContextType.NONE),
        List.of(), "What is binary search?", chunks::add);
    System.out.println(answer);

    assertThat(answer).isNotBlank();
    assertThat(chunks).isNotEmpty();
    assertThat(answer).isEqualTo(String.join("", chunks));
  }

  @Test
  void respondStreamWithLeetcodeContextReturnsAnswerMatchingStreamedChunks() {
    List<String> chunks = new ArrayList<>();
    String answer = workerService.respondStream(List.of(ContextType.LEETCODE),
        List.of(), "What is your LeetCode rating?", chunks::add);
    System.out.println(answer);

    assertThat(answer).isNotBlank();
    assertThat(chunks).isNotEmpty();
    assertThat(answer).isEqualTo(String.join("", chunks));
  }
}
