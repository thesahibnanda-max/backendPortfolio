package net.sahibnanda.portfolio.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import net.sahibnanda.portfolio.config.GroqProperties;
import net.sahibnanda.portfolio.models.GroqCallRequest;
import net.sahibnanda.portfolio.models.GroqCallResponse;
import net.sahibnanda.portfolio.utils.EnvironmentUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Live Groq API check, confirmed working manually against a real key from a
 * local {@code .env} (not committed). Disabled by default since that key will
 * be removed; the {@link #setup()} assumption also makes this self-skip if
 * {@code GROQ_API_KEYS} is ever absent.
 */
@Disabled("Manual-only: requires a real GROQ_API_KEYS in a local .env.")
class GroqClientTest {

  private static GroqClient groqClient;

  @BeforeAll
  static void setup() {
    EnvironmentUtils.loadDotenv();
    String apiKeys = EnvironmentUtils.get("GROQ_API_KEYS");
    assumeTrue(!StringUtils.isEmpty(apiKeys),
        "GROQ_API_KEYS not configured; skipping live Groq API test.");

    groqClient = new GroqClient(new GroqProperties("https://api.groq.com",
        List.of(apiKeys.split(","))));
  }

  @Test
  void callReturnsChatCompletion() {
    GroqCallRequest request = GroqCallRequest.builder()
        .model("llama-3.1-8b-instant")
        .messages(List.of(GroqCallRequest.Message.builder().role("user")
            .content("Reply with exactly one word: pong").build()))
        .temperature(0).maxCompletionTokens(16).topP(1).stream(false).build();

    GroqCallResponse response = groqClient.call(request);
    System.out.println(response);

    assertThat(response).isNotNull();
    assertThat(response.getId()).isNotBlank();
    assertThat(response.getChoices()).isNotEmpty();
    assertThat(response.getChoices().get(0).getMessage().getContent())
        .isNotBlank();
  }
}
