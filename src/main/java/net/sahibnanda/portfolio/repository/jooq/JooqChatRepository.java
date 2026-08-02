package net.sahibnanda.portfolio.repository.jooq;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import net.sahibnanda.portfolio.entity.ChatEntity;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.exception.DatabaseOperationException;
import net.sahibnanda.portfolio.jooq.Tables;
import net.sahibnanda.portfolio.jooq.tables.records.ChatsRecord;
import net.sahibnanda.portfolio.repository.ChatRepository;
import net.sahibnanda.portfolio.utils.JsonUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

/** jOOQ-backed implementation of {@link ChatRepository}. */
@Repository
// Suppress Sonar warning for LocalDateTime.now() usage: it is used
// only for timestamping purposes, which is acceptable here.
@SuppressWarnings("java:S8688")
public class JooqChatRepository implements ChatRepository {

  /** jOOQ context used to execute chat queries. */
  private final DSLContext dslContext;

  /**
   * Creates a new chat repository.
   *
   * @param jooqDslContext the jOOQ context used to execute queries
   */
  public JooqChatRepository(final DSLContext jooqDslContext) {
    this.dslContext = jooqDslContext;
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
      throw new DatabaseOperationException("Failed to create chat: " + chatId,
          e);
    }
    return ChatEntity.builder().chatId(chatId).username(username)
        .chatTitle(chatTitle).messages(sortedMessages).createdAt(now)
        .updatedAt(now).build();
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
      throw new DatabaseOperationException(
          "Failed to find chats for user: " + username, e);
    }
  }

  /**
   * Replaces the messages stored for a chat.
   *
   * @param chatId the identifier of the chat to update
   * @param messages the messages to persist for the chat
   * @throws ChatNotFoundException if no chat exists with the given identifier
   * @throws DatabaseOperationException if the update fails
   */
  @Override
  public void saveMessages(final String chatId, final List<Message> messages) {
    List<Message> sortedMessages = Message.sortedByTimestamp(messages);
    int updated;
    try {
      updated = dslContext.update(Tables.CHATS)
          .set(Tables.CHATS.MESSAGES, serializeMessages(sortedMessages))
          .set(Tables.CHATS.UPDATED_AT, LocalDateTime.now())
          .where(Tables.CHATS.CHAT_ID.eq(chatId)).execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException(
          "Failed to save messages for chat: " + chatId, e);
    }
    if (updated == 0) {
      throw new ChatNotFoundException(chatId);
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
      throw new DatabaseOperationException("Failed to delete chat: " + chatId,
          e);
    }
    if (deleted == 0) {
      throw new ChatNotFoundException(chatId);
    }
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
    int updated;
    try {
      updated =
          dslContext.update(Tables.CHATS).set(Tables.CHATS.CHAT_TITLE, title)
              .set(Tables.CHATS.UPDATED_AT, LocalDateTime.now())
              .where(Tables.CHATS.CHAT_ID.eq(chatId)).execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException(
          "Failed to update title for chat: " + chatId, e);
    }
    if (updated == 0) {
      throw new ChatNotFoundException(chatId);
    }
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
      throw new DatabaseOperationException(
          "Failed to deserialize chat messages", e);
    }
  }
}
