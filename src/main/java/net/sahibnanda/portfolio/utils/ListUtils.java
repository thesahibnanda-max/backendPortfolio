package net.sahibnanda.portfolio.utils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ListUtils {

  public <T> T getRandomElement(List<T> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    return list.get(ThreadLocalRandom.current().nextInt(list.size()));
  }
}
