package net.sahibnanda.portfolio.utils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class ListUtils {

  /**
   * Returns a random element from the list, or null if empty.
   *
   * @param <T> the element type
   * @param list the list to pick from
   * @return a random element, or null if the list is null or empty
   */
  public <T> T getRandomElement(final List<T> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    return list.get(ThreadLocalRandom.current().nextInt(list.size()));
  }
}
