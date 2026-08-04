package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;
import net.sahibnanda.portfolio.client.GroqClient;
import net.sahibnanda.portfolio.config.GroqProperties;
import net.sahibnanda.portfolio.config.LLMProperties;
import net.sahibnanda.portfolio.enums.GroqModel;
import net.sahibnanda.portfolio.options.LLMCallOptions;
import net.sahibnanda.portfolio.utils.EnvironmentUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import net.sahibnanda.portfolio.utils.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Manual-only: requires a real GROQ_API_KEYS in a local .env.")
class LLMServiceTest {

  private static LLMService llmService;

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
        0.8, 1.4, 0.9, 1.0, 64);

    llmService = new LLMService(groqClient, llmProperties);
  }

  @Test
  void callWithFullyRandomSamplingReturnsReply() {
    String reply = llmService
        .call(LLMCallOptions.builder().systemPrompt("Reply concisely.")
            .userPrompt("Reply with exactly one word: pong").build());
    System.out.println(reply);

    assertThat(reply).isNotBlank();
  }

  @Test
  void callWithGivenTemperatureAndTopPReturnsReply() {
    String reply = llmService
        .call(LLMCallOptions.builder().systemPrompt("Reply concisely.")
            .userPrompt("Reply with exactly one word: pong").temperature(0.0)
            .topP(1.0).build());
    System.out.println(reply);

    assertThat(reply).isNotBlank();
  }
}
