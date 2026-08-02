package net.sahibnanda.portfolio.config;

import java.util.List;
import net.sahibnanda.portfolio.utils.ListUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "groq")
public record GroqProperties(String baseUrl, List<String> apiKeys) {

  public String getApiKey() {
    if (apiKeys == null || apiKeys.isEmpty()) {
      throw new IllegalStateException("API keys are not configured");
    }
    return ListUtils.getRandomElement(apiKeys);
  }
}
