package net.sahibnanda.portfolio.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import net.sahibnanda.portfolio.entity.AnonymousChatEntity;
import net.sahibnanda.portfolio.entity.Message;

/**
 * Repository contract for persisting and retrieving anonymous, session-scoped
 * chat data. Deliberately smaller than {@link ChatRepository} -- anonymous
 * chats have no listing, renaming, or explicit delete-by-id affordance in the
 * product; the only removal path is {@link #deleteIdleOlderThan}, used by the
 * cleanup cron.
 */
public interface AnonymousChatRepository {

  /**
   * Creates and persists a new anonymous chat.
   *
   * @param chatId the unique chat identifier (ULID)
   * @param sessionId the session id of the anonymous visitor who owns the chat
   * @param chatTitle the title of the chat
   * @param messages the initial messages belonging to the chat
   * @return the created chat entity
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the chat cannot be persisted
   */
  AnonymousChatEntity create(String chatId, String sessionId, String chatTitle,
      List<Message> messages);

  /**
   * Finds a chat by its identifier.
   *
   * @param chatId the identifier of the chat to look up
   * @return an {@link Optional} containing the chat if one exists with the
   *         given identifier, or an empty {@link Optional} otherwise
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the lookup fails
   */
  Optional<AnonymousChatEntity> findByChatId(String chatId);

  /**
   * Replaces the messages stored for a chat.
   *
   * @param chatId the identifier of the chat to update
   * @param messages the messages to persist for the chat
   * @throws net.sahibnanda.portfolio.exception.ChatNotFoundException if no chat
   *         exists with the given identifier
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the update fails
   */
  void saveMessages(String chatId, List<Message> messages);

  /**
   * Deletes every anonymous chat last updated before {@code cutoff}. Used by
   * the cleanup cron to purge idle anonymous chats.
   *
   * @param cutoff chats with an {@code updatedAt} strictly before this instant
   *        are deleted
   * @return the number of chats deleted
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the deletion fails
   */
  int deleteIdleOlderThan(LocalDateTime cutoff);
}
