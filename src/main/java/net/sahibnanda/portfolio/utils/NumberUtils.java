package net.sahibnanda.portfolio.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class NumberUtils {

  /**
   * Converts a string to a Long, returning a default value if the string is
   * null, empty, or not a valid number.
   *
   * @param value the string to convert
   * @param defaultValue the default value to return if conversion fails
   * @return the converted Long or the default value
   */
  public Long toLong(final String value, final Long defaultValue) {
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException _) {
      return defaultValue;
    }
  }
}
