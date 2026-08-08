package net.sahibnanda.portfolio.repository.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import net.sahibnanda.portfolio.entity.AnonymousChatEntity;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.jooq.Tables;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqAnonymousChatRepositoryTest
    extends AbstractRepositoryIntegrationTest {

  @Autowired
  private JooqAnonymousChatRepository anonymousChatRepository;
  @Autowired
  private DSLContext dslContext;

  @Test
  void createPersistsChatWithMessagesSortedByTimestamp() {
    String chatId = StringUtils.generateUlid();
    String sessionId = StringUtils.generateUlid();
    Message later = new Message(Role.ASSISTANT, "hi back",
        Instant.parse("2026-08-02T12:00:05Z"));
    Message earlier =
        new Message(Role.USER, "hi", Instant.parse("2026-08-02T12:00:00Z"));

    AnonymousChatEntity created = anonymousChatRepository.create(chatId,
        sessionId, "New chat", List.of(later, earlier));

    assertThat(created.getChatId()).isEqualTo(chatId);
    assertThat(created.getSessionId()).isEqualTo(sessionId);
    assertThat(created.getMessages()).containsExactly(earlier, later);
  }

  @Test
  void createRejectsNonUlidChatId() {
    assertThatThrownBy(() -> anonymousChatRepository.create("not-a-ulid",
        StringUtils.generateUlid(), "New chat", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void findByChatIdReturnsEmptyWhenAbsent() {
    Optional<AnonymousChatEntity> found =
        anonymousChatRepository.findByChatId(StringUtils.generateUlid());

    assertThat(found).isEmpty();
  }

  @Test
  void saveMessagesReplacesAndSortsMessages() {
    String chatId = StringUtils.generateUlid();
    anonymousChatRepository.create(chatId, StringUtils.generateUlid(),
        "New chat", List.of());
    Message later = new Message(Role.ASSISTANT, "hi back",
        Instant.parse("2026-08-02T12:00:05Z"));
    Message earlier =
        new Message(Role.USER, "hi", Instant.parse("2026-08-02T12:00:00Z"));

    anonymousChatRepository.saveMessages(chatId, List.of(later, earlier));

    AnonymousChatEntity updated =
        anonymousChatRepository.findByChatId(chatId).orElseThrow();
    assertThat(updated.getMessages()).containsExactly(earlier, later);
  }

  @Test
  void saveMessagesThrowsWhenChatMissing() {
    assertThatThrownBy(() -> anonymousChatRepository
        .saveMessages(StringUtils.generateUlid(), List.of()))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void deleteIdleOlderThanRemovesOnlyStaleChats() {
    String staleChatId = StringUtils.generateUlid();
    anonymousChatRepository.create(staleChatId, StringUtils.generateUlid(),
        "New chat", List.of());
    String freshChatId = StringUtils.generateUlid();
    anonymousChatRepository.create(freshChatId, StringUtils.generateUlid(),
        "New chat", List.of());

    LocalDateTime backdated = LocalDateTime.now().minusMinutes(11);
    dslContext.update(Tables.ANONYMOUS_CHATS)
        .set(Tables.ANONYMOUS_CHATS.UPDATED_AT, backdated)
        .where(Tables.ANONYMOUS_CHATS.CHAT_ID.eq(staleChatId)).execute();

    int deleted = anonymousChatRepository
        .deleteIdleOlderThan(LocalDateTime.now().minusMinutes(10));

    assertThat(deleted).isEqualTo(1);
    assertThat(anonymousChatRepository.findByChatId(staleChatId)).isEmpty();
    assertThat(anonymousChatRepository.findByChatId(freshChatId)).isPresent();
  }
}
