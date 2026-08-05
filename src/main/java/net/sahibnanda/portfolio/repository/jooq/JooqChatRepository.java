package net.sahibnanda.portfolio.repository.jooq;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.dto.ChatObserverDTO;
import net.sahibnanda.portfolio.entity.ChatEntity;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.enums.ChatObserverStatus;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.exception.DatabaseOperationException;
import net.sahibnanda.portfolio.jooq.Tables;
import net.sahibnanda.portfolio.jooq.tables.records.ChatsRecord;
import net.sahibnanda.portfolio.repository.ChatRepository;
import net.sahibnanda.portfolio.repository.observers.ChatRepositoryObserver;
import net.sahibnanda.portfolio.utils.JsonUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

/** jOOQ-backed implementation of {@link ChatRepository}. */
@Slf4j
@Repository
// Suppress Sonar warning for LocalDateTime.now() usage: it is used
// only for timestamping purposes, which is acceptable here.
@SuppressWarnings("java:S8688")
public class JooqChatRepository implements ChatRepository {

  /** jOOQ context used to execute chat queries. */
  private final DSLContext dslContext;

  /** Notified after every chat create, update, or delete. */
  private final ChatRepositoryObserver chatRepositoryObserver;

  /**
   * Creates a new chat repository.
   *
   * @param jooqDslContext the jOOQ context used to execute queries
   * @param observer notified after every chat create, update, or delete
   */
  public JooqChatRepository(final DSLContext jooqDslContext,
      final ChatRepositoryObserver observer) {
    this.dslContext = jooqDslContext;
    this.chatRepositoryObserver = observer;
  }

  /**
   * Creates and persists a new chat.
   *
   * @param chatId the unique chat identifier (ULID)
   * @param username the username of the chat owner
   * @param chatTitle the title of the chat
   * @param messages the initial messages belonging to the chat
   * @return the created chat entity
   * @throws IllegalArgumentException if {@code chatId} is not a valid ULID
   * @throws DatabaseOperationException if the chat cannot be persisted
   */
  @Override
  public ChatEntity create(final String chatId, final String username,
      final String chatTitle, final List<Message> messages) {
    if (!StringUtils.isValidUlid(chatId)) {
      throw new IllegalArgumentException(
          "chatId is not a valid ULID: " + chatId);
    }
    // Use LocalDateTime.now() for timestamping purposes
    LocalDateTime now = LocalDateTime.now();
    List<Message> sortedMessages = Message.sortedByTimestamp(messages);
    try {
      dslContext.insertInto(Tables.CHATS).set(Tables.CHATS.CHAT_ID, chatId)
          .set(Tables.CHATS.USERNAME, username)
          .set(Tables.CHATS.CHAT_TITLE, chatTitle)
          .set(Tables.CHATS.MESSAGES, serializeMessages(sortedMessages))
          .set(Tables.CHATS.CREATED_AT, now).set(Tables.CHATS.UPDATED_AT, now)
          .execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      log.error("Failed to create chat: {}", chatId, e);
      throw new DatabaseOperationException("Failed to create chat: " + chatId,
          e);
    }
    ChatEntity created = ChatEntity.builder().chatId(chatId).username(username)
        .chatTitle(chatTitle).messages(sortedMessages).createdAt(now)
        .updatedAt(now).build();
    chatRepositoryObserver.notifyAllObservers(ChatObserverDTO.builder()
        .chatId(created.getChatId()).username(created.getUsername())
        .chatTitle(created.getChatTitle()).messages(created.getMessages())
        .createdAt(created.getCreatedAt()).updatedAt(created.getUpdatedAt())
        .status(ChatObserverStatus.CHAT_CREATED).build());
    return created;
  }

  /**
   * Finds a chat by its identifier.
   *
   * @param chatId the identifier of the chat to look up
   * @return an {@link Optional} containing the chat if one exists with the
   *         given identifier, or an empty {@link Optional} otherwise
   * @throws DatabaseOperationException if the lookup fails
   */
  @Override
  public Optional<ChatEntity> findByChatId(final String chatId) {
    try {
      return dslContext.selectFrom(Tables.CHATS)
          .where(Tables.CHATS.CHAT_ID.eq(chatId)).fetchOptional(this::toEntity);
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      log.error("Failed to find chat: {}", chatId, e);
      throw new DatabaseOperationException("Failed to find chat: " + chatId, e);
    }
  }

  /**
   * Finds all chats belonging to the given user.
   *
   * @param username the username whose chats are requested
   * @return the chats owned by the user, newest first, or an empty list if the
   *         user has no chats
   * @throws DatabaseOperationException if the lookup fails
   */
  @Override
  public List<ChatEntity> findChats(final String username) {
    try {
      return dslContext.selectFrom(Tables.CHATS)
          .where(Tables.CHATS.USERNAME.eq(username))
          .orderBy(Tables.CHATS.CREATED_AT.desc(), Tables.CHATS.CHAT_ID.desc())
          .fetch(this::toEntity);
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      log.error("Failed to find chats for user: {}", username, e);
      throw new DatabaseOperationException(
          "Failed to find chats for user: " + username, e);
    }
  }

  /**
   * Replaces the messages stored for a chat. Publishes a
   * {@link ChatObserverStatus#CHAT_MESSAGE_SAVED_USER} or
   * {@link ChatObserverStatus#CHAT_MESSAGE_SAVED_ASSISTANT} event matching the
   * role of the most recently timestamped message in {@code messages}, or no
   * event at all if {@code messages} is empty.
   *
   * @param chatId the identifier of the chat to update
   * @param messages the messages to persist for the chat
   * @throws ChatNotFoundException if no chat exists with the given identifier
   * @throws DatabaseOperationException if the update fails
   */
  @Override
  public void saveMessages(final String chatId, final List<Message> messages) {
    List<Message> sortedMessages = Message.sortedByTimestamp(messages);
    // Use LocalDateTime.now() for timestamping purposes
    LocalDateTime now = LocalDateTime.now();
    int updated;
    try {
      updated = dslContext.update(Tables.CHATS)
          .set(Tables.CHATS.MESSAGES, serializeMessages(sortedMessages))
          .set(Tables.CHATS.UPDATED_AT, now)
          .where(Tables.CHATS.CHAT_ID.eq(chatId)).execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      log.error("Failed to save messages for chat: {}", chatId, e);
      throw new DatabaseOperationException(
          "Failed to save messages for chat: " + chatId, e);
    }
    if (updated == 0) {
      throw new ChatNotFoundException(chatId);
    }
    if (!sortedMessages.isEmpty()) {
      chatRepositoryObserver.notifyAllObservers(ChatObserverDTO.builder()
          .chatId(chatId).messages(sortedMessages).updatedAt(now)
          .status(messageSavedStatus(sortedMessages.getLast())).build());
    }
  }

  /**
   * Deletes a chat.
   *
   * @param chatId the identifier of the chat to delete
   * @throws ChatNotFoundException if no chat exists with the given identifier
   * @throws DatabaseOperationException if the deletion fails
   */
  @Override
  public void delete(final String chatId) {
    int deleted;
    try {
      deleted = dslContext.deleteFrom(Tables.CHATS)
          .where(Tables.CHATS.CHAT_ID.eq(chatId)).execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      log.error("Failed to delete chat: {}", chatId, e);
      throw new DatabaseOperationException("Failed to delete chat: " + chatId,
          e);
    }
    if (deleted == 0) {
      throw new ChatNotFoundException(chatId);
    }
    chatRepositoryObserver.notifyAllObservers(ChatObserverDTO.builder()
        .chatId(chatId).status(ChatObserverStatus.CHAT_DELETED).build());
  }

  /**
   * Updates the title of a chat.
   *
   * @param chatId the identifier of the chat to update
   * @param title the new title for the chat
   * @throws ChatNotFoundException if no chat exists with the given identifier
   * @throws DatabaseOperationException if the update fails
   */
  @Override
  public void updateChatTitle(final String chatId, final String title) {
    // Use LocalDateTime.now() for timestamping purposes
    LocalDateTime now = LocalDateTime.now();
    int updated;
    try {
      updated = dslContext.update(Tables.CHATS)
          .set(Tables.CHATS.CHAT_TITLE, title).set(Tables.CHATS.UPDATED_AT, now)
          .where(Tables.CHATS.CHAT_ID.eq(chatId)).execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      log.error("Failed to update title for chat: {}", chatId, e);
      throw new DatabaseOperationException(
          "Failed to update title for chat: " + chatId, e);
    }
    if (updated == 0) {
      throw new ChatNotFoundException(chatId);
    }
    chatRepositoryObserver.notifyAllObservers(
        ChatObserverDTO.builder().chatId(chatId).chatTitle(title).updatedAt(now)
            .status(ChatObserverStatus.CHAT_TITLE_UPDATED).build());
  }

  /**
   * The event status for a message-saved notification, based on who authored
   * the most recently saved message.
   *
   * @param lastMessage the most recently timestamped message in the list just
   *        persisted
   * @return {@link ChatObserverStatus#CHAT_MESSAGE_SAVED_USER} or
   *         {@link ChatObserverStatus#CHAT_MESSAGE_SAVED_ASSISTANT}
   */
  private ChatObserverStatus messageSavedStatus(final Message lastMessage) {
    return lastMessage.role() == Role.USER
        ? ChatObserverStatus.CHAT_MESSAGE_SAVED_USER
        : ChatObserverStatus.CHAT_MESSAGE_SAVED_ASSISTANT;
  }

  private ChatEntity toEntity(final ChatsRecord chatRecord) {
    return ChatEntity.builder().chatId(chatRecord.getChatId())
        .username(chatRecord.getUsername()).chatTitle(chatRecord.getChatTitle())
        .messages(Message
            .sortedByTimestamp(deserializeMessages(chatRecord.getMessages())))
        .createdAt(chatRecord.getCreatedAt())
        .updatedAt(chatRecord.getUpdatedAt()).build();
  }

  private JSONB serializeMessages(final List<Message> messages) {
    return JSONB.jsonb(JsonUtils.toJson(messages));
  }

  private List<Message> deserializeMessages(final JSONB jsonb) {
    try {
      return JsonUtils.fromJson(jsonb.data(),
          new TypeReference<List<Message>>() {
          });
    } catch (RuntimeException e) {
      log.error("Failed to deserialize chat messages", e);
      throw new DatabaseOperationException(
          "Failed to deserialize chat messages", e);
    }
  }
}
