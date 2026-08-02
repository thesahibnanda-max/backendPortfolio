package net.sahibnanda.portfolio.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StringUtils {

  public boolean isEmpty(String str) {
    return str == null || str.trim().isEmpty();
  }

  public boolean equalsIgnoreCase(String a, String b) {
    if (a == null && b == null) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.equalsIgnoreCase(b);
  }
}
