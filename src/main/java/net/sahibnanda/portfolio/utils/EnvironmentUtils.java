package net.sahibnanda.portfolio.utils;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class EnvironmentUtils {

  /**
   * Loads variables from a {@code .env} file (if present) into system
   * properties, without overriding any that are already set.
   */
  public void loadDotenv() {
    Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    dotenv.entries().forEach(entry -> System.getProperties()
        .putIfAbsent(entry.getKey(), entry.getValue()));
  }

  /**
   * Returns the value of an environment variable, checking system environment
   * variables first, then system properties.
   *
   * @param key the name of the variable to look up
   * @return the resolved value, or {@code null} if not set
   */
  public String get(final String key) {
    return System.getenv(key) != null ? System.getenv(key)
        : System.getProperty(key);
  }
}
