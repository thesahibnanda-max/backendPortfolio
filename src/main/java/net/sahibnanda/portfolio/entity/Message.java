package net.sahibnanda.portfolio.entity;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record Message(Role role, String message, Instant timestamp) {

  public Message {
    Objects.requireNonNull(role, "role must not be null");
    Objects.requireNonNull(message, "message must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
  }

  public static List<Message> sortedByTimestamp(List<Message> messages) {
    Objects.requireNonNull(messages, "messages must not be null");
    return messages.stream().sorted(Comparator.comparing(Message::timestamp)).toList();
  }
}
