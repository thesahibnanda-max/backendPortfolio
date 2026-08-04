package net.sahibnanda.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.enums.ChatObserverStatus;

/**
 * A chat lifecycle event published to Kafka by the chat repository observer.
 * Fields not relevant to a given {@link #status} are left {@code null} rather
 * than re-fetched, to avoid an extra database round trip on every chat write.
 */
@SuperBuilder
@Jacksonized
@Getter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class ChatObserverDTO {

  /** The chat's unique identifier. */
  private final String chatId;

  /** The chat owner's username, if known for this event. */
  private final String username;

  /** The chat's title, if known for this event. */
  private final String chatTitle;

  /** The chat's messages, if known for this event. */
  private final List<Message> messages;

  /** When the chat was created, if known for this event. */
  private final LocalDateTime createdAt;

  /** When the chat was last updated, if known for this event. */
  private final LocalDateTime updatedAt;

  /** The lifecycle event this DTO represents. */
  private final ChatObserverStatus status;
}
