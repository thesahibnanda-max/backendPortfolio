package net.sahibnanda.portfolio.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class JsonUtilsTest {

  private record InstantHolder(Instant timestamp) {
  }

  @Test
  void roundTripsInstantAsIso8601() {
    Instant original = Instant.parse("2026-08-02T12:00:00Z");

    String json = JsonUtils.toJson(new InstantHolder(original));
    InstantHolder deserialized = JsonUtils.fromJson(json, InstantHolder.class);

    assertThat(json).contains("2026-08-02T12:00:00Z");
    assertThat(deserialized.timestamp()).isEqualTo(original);
  }
}
