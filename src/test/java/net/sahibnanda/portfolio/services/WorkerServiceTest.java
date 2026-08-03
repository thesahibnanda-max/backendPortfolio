package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Manual-only: requires a real GROQ_API_KEYS in a local .env.")
class WorkerServiceTest {

  private static WorkerService workerService;

  @BeforeAll
  static void setup() {
    EnvironmentUtils.loadDotenv();
    String apiKeys = EnvironmentUtils.get("GROQ_API_KEYS");
    assumeTrue(!StringUtils.isEmpty(apiKeys),
        "GROQ_API_KEYS not configured; skipping live Groq API test.");

    GroqClient groqClient =
        new GroqClient(new GroqProperties("https://api.groq.com",
            List.of(apiKeys.split(","))));
    LLMProperties llmProperties = new LLMProperties(
        Map.of(GroqModel.LLAMA_3_3_70B, 50, GroqModel.GPT_OSS_20B, 20,
            GroqModel.QWEN_3_6_27B, 20, GroqModel.GPT_OSS_120B, 10),
        0.8, 1.4, 0.9, 1.0, 2048);
    LLMService llmService = new LLMService(groqClient, llmProperties);

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
    ContextAggregatorService contextAggregatorService =
        new ContextAggregatorService(detailsService);

    workerService = new WorkerService(llmService, new PromptTemplates(),
        contextAggregatorService);
  }

  @Test
  void respondWithLeetcodeContextAnswersNaturally() {
    String answer = workerService.respond(List.of(ContextType.LEETCODE),
        List.of(), "What is your LeetCode rating?");
    System.out.println(answer);

    assertThat(answer).isNotBlank();
  }

  @Test
  void respondWithNoContextStillAnswersGenerally() {
    String answer = workerService.respond(List.of(ContextType.NONE), List.of(),
        "What is binary search?");
    System.out.println(answer);

    assertThat(answer).isNotBlank();
  }
}
