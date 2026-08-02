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

@Repository
@SuppressWarnings(
    "java:S8688") // Suppress Sonar warning for LocalDateTime.now() usage, as it is used for
// timestamping purposes which is acceptable in this context.
public class JooqChatRepository implements ChatRepository {

  private final DSLContext dslContext;

  public JooqChatRepository(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  @Override
  public ChatEntity create(
      String chatId, String username, String chatTitle, List<Message> messages) {
    if (!StringUtils.isValidUlid(chatId)) {
      throw new IllegalArgumentException("chatId is not a valid ULID: " + chatId);
    }
    LocalDateTime now = LocalDateTime.now(); // Use LocalDateTime.now() for timestamping purposes
    List<Message> sortedMessages = Message.sortedByTimestamp(messages);
    try {
      dslContext
          .insertInto(Tables.CHATS)
          .set(Tables.CHATS.CHAT_ID, chatId)
          .set(Tables.CHATS.USERNAME, username)
          .set(Tables.CHATS.CHAT_TITLE, chatTitle)
          .set(Tables.CHATS.MESSAGES, serializeMessages(sortedMessages))
          .set(Tables.CHATS.CREATED_AT, now)
          .set(Tables.CHATS.UPDATED_AT, now)
          .execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException("Failed to create chat: " + chatId, e);
    }
    return ChatEntity.builder()
        .chatId(chatId)
        .username(username)
        .chatTitle(chatTitle)
        .messages(sortedMessages)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  @Override
  public Optional<ChatEntity> findByChatId(String chatId) {
    try {
      return dslContext
          .selectFrom(Tables.CHATS)
          .where(Tables.CHATS.CHAT_ID.eq(chatId))
          .fetchOptional(this::toEntity);
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException("Failed to find chat: " + chatId, e);
    }
  }

  @Override
  public List<ChatEntity> findChats(String username) {
    try {
      return dslContext
          .selectFrom(Tables.CHATS)
          .where(Tables.CHATS.USERNAME.eq(username))
          .orderBy(Tables.CHATS.CREATED_AT.desc(), Tables.CHATS.CHAT_ID.desc())
          .fetch(this::toEntity);
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException("Failed to find chats for user: " + username, e);
    }
  }

  @Override
  public void saveMessages(String chatId, List<Message> messages) {
    List<Message> sortedMessages = Message.sortedByTimestamp(messages);
    int updated;
    try {
      updated =
          dslContext
              .update(Tables.CHATS)
              .set(Tables.CHATS.MESSAGES, serializeMessages(sortedMessages))
              .set(Tables.CHATS.UPDATED_AT, LocalDateTime.now())
              .where(Tables.CHATS.CHAT_ID.eq(chatId))
              .execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException("Failed to save messages for chat: " + chatId, e);
    }
    if (updated == 0) {
      throw new ChatNotFoundException(chatId);
    }
  }

  @Override
  public void delete(String chatId) {
    int deleted;
    try {
      deleted =
          dslContext.deleteFrom(Tables.CHATS).where(Tables.CHATS.CHAT_ID.eq(chatId)).execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException("Failed to delete chat: " + chatId, e);
    }
    if (deleted == 0) {
      throw new ChatNotFoundException(chatId);
    }
  }

  @Override
  public void updateChatTitle(String chatId, String title) {
    int updated;
    try {
      updated =
          dslContext
              .update(Tables.CHATS)
              .set(Tables.CHATS.CHAT_TITLE, title)
              .set(Tables.CHATS.UPDATED_AT, LocalDateTime.now())
              .where(Tables.CHATS.CHAT_ID.eq(chatId))
              .execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException("Failed to update title for chat: " + chatId, e);
    }
    if (updated == 0) {
      throw new ChatNotFoundException(chatId);
    }
  }

  private ChatEntity toEntity(ChatsRecord chatRecord) {
    return ChatEntity.builder()
        .chatId(chatRecord.getChatId())
        .username(chatRecord.getUsername())
        .chatTitle(chatRecord.getChatTitle())
        .messages(Message.sortedByTimestamp(deserializeMessages(chatRecord.getMessages())))
        .createdAt(chatRecord.getCreatedAt())
        .updatedAt(chatRecord.getUpdatedAt())
        .build();
  }

  private JSONB serializeMessages(List<Message> messages) {
    return JSONB.jsonb(JsonUtils.toJson(messages));
  }

  private List<Message> deserializeMessages(JSONB jsonb) {
    try {
      return JsonUtils.fromJson(jsonb.data(), new TypeReference<List<Message>>() {});
    } catch (RuntimeException e) {
      throw new DatabaseOperationException("Failed to deserialize chat messages", e);
    }
  }
}
