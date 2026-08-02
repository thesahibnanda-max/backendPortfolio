package net.sahibnanda.portfolio.entity;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChatEntity {
  /** Unique identifier of the chat. */
  private String chatId;

  /** Username of the chat owner. */
  private String username;

  /** Display title of the chat. */
  private String chatTitle;

  /** Ordered messages belonging to the chat. */
  private List<Message> messages;

  /** Timestamp when the chat was created. */
  private LocalDateTime createdAt;

  /** Timestamp when the chat was last updated. */
  private LocalDateTime updatedAt;
}
