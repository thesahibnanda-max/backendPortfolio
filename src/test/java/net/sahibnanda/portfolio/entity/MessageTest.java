package net.sahibnanda.portfolio.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageTest {

  @Test
  void sortedByTimestampOrdersAscendingRegardlessOfInputOrder() {
    Message earliest =
        new Message(Role.USER, "hi", Instant.parse("2026-08-02T12:00:00Z"));
    Message middle = new Message(Role.ASSISTANT, "hello",
        Instant.parse("2026-08-02T12:00:05Z"));
    Message latest =
        new Message(Role.USER, "bye", Instant.parse("2026-08-02T12:00:10Z"));

    List<Message> sorted =
        Message.sortedByTimestamp(List.of(latest, earliest, middle));

    assertThat(sorted).containsExactly(earliest, middle, latest);
  }

  @Test
  void constructorRejectsNullTimestamp() {
    assertThatThrownBy(() -> new Message(Role.USER, "hi", null))
        .isInstanceOf(NullPointerException.class);
  }
}
