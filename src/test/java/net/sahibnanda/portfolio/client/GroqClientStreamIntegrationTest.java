package net.sahibnanda.portfolio.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import net.sahibnanda.portfolio.config.GroqProperties;
import net.sahibnanda.portfolio.models.GroqCallRequest;
import net.sahibnanda.portfolio.utils.EnvironmentUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import net.sahibnanda.portfolio.utils.TestEnvironment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Live Groq streaming API check, run manually against a real key. Disabled by
 * default.
 *
 * <p>
 * To run: export the {@code GROQ_API_KEYS} value from this project's
 * {@code stage.env} into your shell before running Maven, e.g. (bash):
 * 
 * <pre>{@code
 * export GROQ_API_KEYS=$(grep '^GROQ_API_KEYS=' stage.env | cut -d= -f2-)
 * mvn -Dtest=GroqClientStreamIntegrationTest test
 * }</pre>
 * 
 * then temporarily remove the {@code @Disabled} annotation (or run via your
 * IDE's "run single test" with @Disabled re-enabled afterward). Never commit
 * {@code stage.env} or export its contents in CI.
 */
@Disabled("Manual-only: requires a real GROQ_API_KEYS (stage) exported in the shell.")
class GroqClientStreamIntegrationTest {

  private static GroqClient groqClient;

  @BeforeAll
  static void setup() {
    EnvironmentUtils.loadDotenv();
    String apiKeys = EnvironmentUtils.get("GROQ_API_KEYS");
    assumeTrue(!StringUtils.isEmpty(apiKeys),
        "GROQ_API_KEYS not configured; skipping live Groq API test.");

    groqClient =
        new GroqClient(new GroqProperties(TestEnvironment.GROQ_BASE_URL,
            List.of(apiKeys.split(","))));
  }

  @Test
  void callStreamReceivesIncrementalChunksAndConcatenatesCorrectly() {
    List<String> chunks = new ArrayList<>();
    GroqCallRequest request = GroqCallRequest.builder()
        .model("llama-3.1-8b-instant")
        .messages(List.of(GroqCallRequest.Message.builder().role("user")
            .content("Count from 1 to 5, one number per line.").build()))
        .temperature(0).maxCompletionTokens(64).topP(1).stream(true).build();

    String full = groqClient.callStream(request, chunks::add);

    assertThat(chunks).isNotEmpty();
    assertThat(full).isEqualTo(String.join("", chunks));
    System.out.println(full);
  }
}
