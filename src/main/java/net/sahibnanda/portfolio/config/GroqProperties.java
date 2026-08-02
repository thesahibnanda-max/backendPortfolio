package net.sahibnanda.portfolio.config;

import java.util.List;
import net.sahibnanda.portfolio.utils.ListUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Groq API client.
 *
 * @param baseUrl base URL of the Groq API
 * @param apiKeys API keys used to authenticate with the Groq API
 */
@ConfigurationProperties(prefix = "groq")
public record GroqProperties(String baseUrl, List<String> apiKeys) {

  /**
   * Canonical constructor; defensively copies {@code apiKeys} into an immutable
   * list so the stored reference can never be mutated by external code
   * (SpotBugs EI_EXPOSE_REP2), and so the accessor cannot expose a mutable
   * internal list either (EI_EXPOSE_REP).
   */
  public GroqProperties {
    apiKeys = apiKeys == null ? List.of() : List.copyOf(apiKeys);
  }

  /**
   * Returns a randomly selected API key from the configured list of keys.
   *
   * @return a randomly chosen API key
   * @throws IllegalStateException if no API keys are configured
   */
  public String getApiKey() {
    if (apiKeys == null || apiKeys.isEmpty()) {
      throw new IllegalStateException("API keys are not configured");
    }
    return ListUtils.getRandomElement(apiKeys);
  }
}
