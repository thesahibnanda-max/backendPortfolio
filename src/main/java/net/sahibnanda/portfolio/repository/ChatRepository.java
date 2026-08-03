package net.sahibnanda.portfolio.repository;

import java.util.List;
import java.util.Optional;
import net.sahibnanda.portfolio.entity.ChatEntity;
import net.sahibnanda.portfolio.entity.Message;

/** Repository contract for persisting and retrieving chat data. */
public interface ChatRepository {

  /**
   * Creates and persists a new chat.
   *
   * @param chatId the unique chat identifier (ULID)
   * @param username the username of the chat owner
   * @param chatTitle the title of the chat
   * @param messages the initial messages belonging to the chat
   * @return the created chat entity
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the chat cannot be persisted
   */
  ChatEntity create(String chatId, String username, String chatTitle,
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
  Optional<ChatEntity> findByChatId(String chatId);

  /**
   * Finds all chats belonging to the given user.
   *
   * @param username the username whose chats are requested
   * @return the chats owned by the user, newest first, or an empty list if the
   *         user has no chats
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the lookup fails
   */
  List<ChatEntity> findChats(String username);

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
   * Deletes a chat.
   *
   * @param chatId the identifier of the chat to delete
   * @throws net.sahibnanda.portfolio.exception.ChatNotFoundException if no chat
   *         exists with the given identifier
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the deletion fails
   */
  void delete(String chatId);

  /**
   * Updates the title of a chat.
   *
   * @param chatId the identifier of the chat to update
   * @param title the new title for the chat
   * @throws net.sahibnanda.portfolio.exception.ChatNotFoundException if no chat
   *         exists with the given identifier
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the update fails
   */
  void updateChatTitle(String chatId, String title);
}
