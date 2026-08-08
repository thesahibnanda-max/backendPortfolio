package net.sahibnanda.portfolio.repository.jooq;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.entity.AnonymousChatEntity;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.exception.DatabaseOperationException;
import net.sahibnanda.portfolio.jooq.Tables;
import net.sahibnanda.portfolio.jooq.tables.records.AnonymousChatsRecord;
import net.sahibnanda.portfolio.repository.AnonymousChatRepository;
import net.sahibnanda.portfolio.utils.JsonUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

/**
 * jOOQ-backed implementation of {@link AnonymousChatRepository}. Unlike
 * {@link JooqChatRepository}, this deliberately publishes no
 * repository-observer events -- anonymous chats are ephemeral (purged by the
 * cleanup cron) and never feed the Kafka/OpenSearch search-indexing pipeline,
 * since search stays an authenticated-only feature.
 */
@Slf4j
@Repository
// Suppress Sonar warning for LocalDateTime.now() usage: it is used
// only for timestamping purposes, which is acceptable here.
@SuppressWarnings("java:S8688")
public class JooqAnonymousChatRepository implements AnonymousChatRepository {

  /** jOOQ context used to execute chat queries. */
  private final DSLContext dslContext;

  /**
   * Creates a new anonymous chat repository.
   *
   * @param jooqDslContext the jOOQ context used to execute queries
   */
  public JooqAnonymousChatRepository(final DSLContext jooqDslContext) {
    this.dslContext = jooqDslContext;
  }

  /**
   * Creates and persists a new anonymous chat.
   *
   * @param chatId the unique chat identifier (ULID)
   * @param sessionId the session id of the anonymous visitor who owns the chat
   * @param chatTitle the title of the chat
   * @param messages the initial messages belonging to the chat
   * @return the created chat entity
   * @throws IllegalArgumentException if {@code chatId} is not a valid ULID
   * @throws DatabaseOperationException if the chat cannot be persisted
   */
  @Override
  public AnonymousChatEntity create(final String chatId, final String sessionId,
      final String chatTitle, final List<Message> messages) {
    if (!StringUtils.isValidUlid(chatId)) {
      throw new IllegalArgumentException(
          "chatId is not a valid ULID: " + chatId);
    }
    // Use LocalDateTime.now() for timestamping purposes
    LocalDateTime now = LocalDateTime.now();
    List<Message> sortedMessages = Message.sortedByTimestamp(messages);
    try {
      dslContext.insertInto(Tables.ANONYMOUS_CHATS)
          .set(Tables.ANONYMOUS_CHATS.CHAT_ID, chatId)
          .set(Tables.ANONYMOUS_CHATS.SESSION_ID, sessionId)
          .set(Tables.ANONYMOUS_CHATS.CHAT_TITLE, chatTitle)
          .set(Tables.ANONYMOUS_CHATS.MESSAGES,
              serializeMessages(sortedMessages))
          .set(Tables.ANONYMOUS_CHATS.CREATED_AT, now)
          .set(Tables.ANONYMOUS_CHATS.UPDATED_AT, now).execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      log.error("Failed to create anonymous chat: {}", chatId, e);
      throw new DatabaseOperationException(
          "Failed to create anonymous chat: " + chatId, e);
    }
    return AnonymousChatEntity.builder().chatId(chatId).sessionId(sessionId)
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
  public Optional<AnonymousChatEntity> findByChatId(final String chatId) {
    try {
      return dslContext.selectFrom(Tables.ANONYMOUS_CHATS)
          .where(Tables.ANONYMOUS_CHATS.CHAT_ID.eq(chatId))
          .fetchOptional(this::toEntity);
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      log.error("Failed to find anonymous chat: {}", chatId, e);
      throw new DatabaseOperationException(
          "Failed to find anonymous chat: " + chatId, e);
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
    // Use LocalDateTime.now() for timestamping purposes
    LocalDateTime now = LocalDateTime.now();
    int updated;
    try {
      updated = dslContext.update(Tables.ANONYMOUS_CHATS)
          .set(Tables.ANONYMOUS_CHATS.MESSAGES,
              serializeMessages(sortedMessages))
          .set(Tables.ANONYMOUS_CHATS.UPDATED_AT, now)
          .where(Tables.ANONYMOUS_CHATS.CHAT_ID.eq(chatId)).execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      log.error("Failed to save messages for anonymous chat: {}", chatId, e);
      throw new DatabaseOperationException(
          "Failed to save messages for anonymous chat: " + chatId, e);
    }
    if (updated == 0) {
      throw new ChatNotFoundException(chatId);
    }
  }

  /**
   * Deletes every anonymous chat last updated before {@code cutoff}.
   *
   * @param cutoff chats with an {@code updatedAt} strictly before this instant
   *        are deleted
   * @return the number of chats deleted
   * @throws DatabaseOperationException if the deletion fails
   */
  @Override
  public int deleteIdleOlderThan(final LocalDateTime cutoff) {
    try {
      return dslContext.deleteFrom(Tables.ANONYMOUS_CHATS)
          .where(Tables.ANONYMOUS_CHATS.UPDATED_AT.lt(cutoff)).execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      log.error("Failed to purge idle anonymous chats older than {}", cutoff,
          e);
      throw new DatabaseOperationException(
          "Failed to purge idle anonymous chats older than " + cutoff, e);
    }
  }

  private AnonymousChatEntity toEntity(final AnonymousChatsRecord chatRecord) {
    return AnonymousChatEntity.builder().chatId(chatRecord.getChatId())
        .sessionId(chatRecord.getSessionId())
        .chatTitle(chatRecord.getChatTitle())
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
      log.error("Failed to deserialize anonymous chat messages", e);
      throw new DatabaseOperationException(
          "Failed to deserialize anonymous chat messages", e);
    }
  }
}
