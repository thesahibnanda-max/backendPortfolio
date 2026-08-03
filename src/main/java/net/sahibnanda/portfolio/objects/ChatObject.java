package net.sahibnanda.portfolio.objects;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import net.sahibnanda.portfolio.entity.Message;

/**
 * A chat's public-facing details, returned by
 * {@link net.sahibnanda.portfolio.services.UserChatService} in place of the raw
 * {@link net.sahibnanda.portfolio.entity.ChatEntity} it's backed by.
 */
@Builder
@Data
public final class ChatObject {

  /** Unique identifier of the chat. */
  private final String chatId;

  /** Username of the chat owner. */
  private final String username;

  /** Display title of the chat. */
  private final String chatTitle;

  /** Ordered messages belonging to the chat. */
  private final List<Message> messages;

  /** Timestamp when the chat was created. */
  private final LocalDateTime createdAt;

  /** Timestamp when the chat was last updated. */
  private final LocalDateTime updatedAt;
}
