package net.sahibnanda.portfolio.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

import lombok.experimental.UtilityClass;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

@UtilityClass
public final class TestEnvironment {

  private static final StandardEnvironment ENVIRONMENT = load();

  public static final String GROQ_BASE_URL = resolve("groq.base-url");
  public static final String LEETCODE_BASE_URL = resolve("leetcode.base-url");
  public static final String CODEFORCES_BASE_URL =
      resolve("codeforces.base-url");
  public static final String GITHUB_BASE_URL = resolve("github.base-url");
  public static final String PROFILE_JSON_RETRIEVAL_URL =
      resolve("profile.profile-json-retreival-url");
  public static final String PERSONALITY_JSON_RETRIEVAL_URL =
      resolve("profile.personality-json-retreival-url");

  private static StandardEnvironment load() {
    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    List<PropertySource<?>> loaded;
    try {
      loaded =
          loader.load("application", new ClassPathResource("application.yml"));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load application.yml for tests",
          e);
    }
    StandardEnvironment environment = new StandardEnvironment();
    loaded.forEach(source -> environment.getPropertySources().addLast(source));
    return environment;
  }

  private static String resolve(final String key) {
    return ENVIRONMENT.resolvePlaceholders(
        Objects.requireNonNull(ENVIRONMENT.getProperty(key)));
  }
}
