package net.sahibnanda.portfolio.utils;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class StringUtils {

  /**
   * Returns true if the string is null, empty, or blank.
   *
   * @param str the string to check
   * @return true if the string is null, empty, or blank
   */
  public boolean isEmpty(final String str) {
    return str == null || str.trim().isEmpty();
  }

  /**
   * Compares two strings for equality, ignoring case and nulls.
   *
   * @param a the first string
   * @param b the second string
   * @return true if the strings are equal ignoring case
   */
  public boolean equalsIgnoreCase(final String a, final String b) {
    if (a == null && b == null) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.equalsIgnoreCase(b);
  }

  /**
   * Generates a new random ULID string.
   *
   * @return the generated ULID string
   */
  public String generateUlid() {
    return UlidCreator.getUlid().toString();
  }

  /**
   * Returns true if the given value is a syntactically valid ULID.
   *
   * @param value the value to check
   * @return true if the value is a valid ULID
   */
  public boolean isValidUlid(final String value) {
    if (isEmpty(value)) {
      return false;
    }
    try {
      Ulid.from(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
