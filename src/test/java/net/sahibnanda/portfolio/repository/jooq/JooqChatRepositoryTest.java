package net.sahibnanda.portfolio.repository.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.sahibnanda.portfolio.entity.ChatEntity;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.repository.UserRepository;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqChatRepositoryTest extends AbstractRepositoryIntegrationTest {

  @Autowired private JooqChatRepository chatRepository;
  @Autowired private UserRepository userRepository;

  @BeforeEach
  void createOwningUser() {
    userRepository.create("alice", "hashed-pw");
  }

  @Test
  void createPersistsChatWithMessagesSortedByTimestamp() {
    String chatId = StringUtils.generateUlid();
    Message later = new Message(Role.ASSISTANT, "hi back", Instant.parse("2026-08-02T12:00:05Z"));
    Message earlier = new Message(Role.USER, "hi", Instant.parse("2026-08-02T12:00:00Z"));

    ChatEntity created = chatRepository.create(chatId, "alice", "Chat 1", List.of(later, earlier));

    assertThat(created.getChatId()).isEqualTo(chatId);
    assertThat(created.getMessages()).containsExactly(earlier, later);
  }

  @Test
  void createRejectsNonUlidChatId() {
    assertThatThrownBy(() -> chatRepository.create("not-a-ulid", "alice", "Chat 1", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void findByChatIdReturnsEmptyWhenAbsent() {
    Optional<ChatEntity> found = chatRepository.findByChatId(StringUtils.generateUlid());

    assertThat(found).isEmpty();
  }

  @Test
  void findChatsOrdersNewestFirst() {
    String firstChatId = StringUtils.generateUlid();
    chatRepository.create(firstChatId, "alice", "Chat 1", List.of());
    String secondChatId = StringUtils.generateUlid();
    chatRepository.create(secondChatId, "alice", "Chat 2", List.of());

    List<ChatEntity> chats = chatRepository.findChats("alice");

    assertThat(chats).extracting(ChatEntity::getChatId).containsExactly(secondChatId, firstChatId);
  }

  @Test
  void saveMessagesReplacesAndSortsMessages() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());
    Message later = new Message(Role.ASSISTANT, "hi back", Instant.parse("2026-08-02T12:00:05Z"));
    Message earlier = new Message(Role.USER, "hi", Instant.parse("2026-08-02T12:00:00Z"));

    chatRepository.saveMessages(chatId, List.of(later, earlier));

    ChatEntity updated = chatRepository.findByChatId(chatId).orElseThrow();
    assertThat(updated.getMessages()).containsExactly(earlier, later);
  }

  @Test
  void saveMessagesThrowsWhenChatMissing() {
    assertThatThrownBy(() -> chatRepository.saveMessages(StringUtils.generateUlid(), List.of()))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void deleteRemovesChat() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    chatRepository.delete(chatId);

    assertThat(chatRepository.findByChatId(chatId)).isEmpty();
  }

  @Test
  void deleteThrowsWhenChatMissing() {
    assertThatThrownBy(() -> chatRepository.delete(StringUtils.generateUlid()))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void updateChatTitleRenamesChat() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    chatRepository.updateChatTitle(chatId, "Renamed Chat");

    assertThat(chatRepository.findByChatId(chatId).orElseThrow().getChatTitle())
        .isEqualTo("Renamed Chat");
  }

  @Test
  void updateChatTitleThrowsWhenChatMissing() {
    assertThatThrownBy(() -> chatRepository.updateChatTitle(StringUtils.generateUlid(), "Renamed"))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void deletingOwningUserCascadesToChats() {
    String chatId = StringUtils.generateUlid();
    chatRepository.create(chatId, "alice", "Chat 1", List.of());

    userRepository.delete("alice");

    assertThat(chatRepository.findByChatId(chatId)).isEmpty();
  }
}
