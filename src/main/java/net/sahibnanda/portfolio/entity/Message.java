package net.sahibnanda.portfolio.entity;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Message(Role role, String message, Instant timestamp) {

  /** Validates that no component of the message is null. */
  public Message {
    Objects.requireNonNull(role, "role must not be null");
    Objects.requireNonNull(message, "message must not be null");
    Objects.requireNonNull(timestamp, "timestamp must not be null");
  }

  /** Returns the given messages sorted by ascending timestamp. */
  public static List<Message> sortedByTimestamp(final List<Message> messages) {
    Objects.requireNonNull(messages, "messages must not be null");
    return messages.stream()
        .sorted(Comparator.comparing(
            m -> Optional.ofNullable(m.timestamp()).orElse(Instant.EPOCH)))
        .toList();
  }
}
