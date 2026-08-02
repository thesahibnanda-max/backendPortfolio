package net.sahibnanda.portfolio.utils;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;
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

  public String generateUlid() {
    return UlidCreator.getUlid().toString();
  }

  public boolean isValidUlid(String value) {
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
