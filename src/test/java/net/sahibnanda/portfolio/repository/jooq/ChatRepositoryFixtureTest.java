package net.sahibnanda.portfolio.repository.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import net.sahibnanda.portfolio.entity.ChatEntity;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ChatRepositoryFixtureTest extends AbstractRepositoryIntegrationTest {

  @Autowired
  private JooqChatRepository chatRepository;

  @BeforeEach
  void loadFixtures() {
    loadFixtureScript("fixtures/repository-test-data.sql");
  }

  @Test
  void findByChatIdReturnsSeededChatWithMessages() {
    ChatEntity chat =
        chatRepository.findByChatId("01ARZ3NDEKTSV4RRFFQ69G5FA1").orElseThrow();

    assertThat(chat.getUsername()).isEqualTo("alice");
    assertThat(chat.getChatTitle()).isEqualTo("Trip Planning");
    assertThat(chat.getMessages()).containsExactly(
        new Message(Role.USER, "Where should I go in Japan?",
            Instant.parse("2026-01-05T09:00:00Z")),
        new Message(Role.ASSISTANT, "Consider Kyoto and Osaka.",
            Instant.parse("2026-01-05T09:01:00Z")));
  }

  @Test
  void findByChatIdReturnsEmptyForUnknownChat() {
    assertThat(chatRepository.findByChatId(StringUtils.generateUlid()))
        .isEmpty();
  }

  @Test
  void findChatsOrdersAlicesSeededChatsNewestFirstWithIdTiebreaker() {
    List<ChatEntity> chats = chatRepository.findChats("alice");

    assertThat(chats).extracting(ChatEntity::getChatId).containsExactly(
        "01ARZ3NDEKTSV4RRFFQ69G5FA4", "01ARZ3NDEKTSV4RRFFQ69G5FA3",
        "01ARZ3NDEKTSV4RRFFQ69G5FA2", "01ARZ3NDEKTSV4RRFFQ69G5FA1");
  }

  @Test
  void findChatsScopesToRequestedUserOnly() {
    List<ChatEntity> bobsChats = chatRepository.findChats("bob");

    assertThat(bobsChats).extracting(ChatEntity::getChatId)
        .containsExactly("01ARZ3NDEKTSV4RRFFQ69G5FB1");
  }

  @Test
  void findChatsReturnsEmptyForUserWithNoChats() {
    assertThat(chatRepository.findChats("carol")).isEmpty();
  }

  @Test
  void createAddsChatAlongsideSeededOnes() {
    String chatId = StringUtils.generateUlid();

    chatRepository.create(chatId, "carol", "Carol's First Chat", List.of());

    assertThat(chatRepository.findChats("carol"))
        .extracting(ChatEntity::getChatId).containsExactly(chatId);
    assertThat(chatRepository.findChats("alice")).hasSize(4);
  }

  @Test
  void saveMessagesReplacesSeededChatsMessages() {
    Message replacement = new Message(Role.USER, "Actually, skip Kyoto.",
        Instant.parse("2026-01-06T10:00:00Z"));

    chatRepository.saveMessages("01ARZ3NDEKTSV4RRFFQ69G5FA2",
        List.of(replacement));

    assertThat(chatRepository.findByChatId("01ARZ3NDEKTSV4RRFFQ69G5FA2")
        .orElseThrow().getMessages()).containsExactly(replacement);
  }

  @Test
  void saveMessagesThrowsForUnknownChat() {
    assertThatThrownBy(() -> chatRepository
        .saveMessages(StringUtils.generateUlid(), List.of()))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void updateChatTitleRenamesSeededChat() {
    chatRepository.updateChatTitle("01ARZ3NDEKTSV4RRFFQ69G5FB1",
        "Renamed Bob Chat");

    assertThat(chatRepository.findByChatId("01ARZ3NDEKTSV4RRFFQ69G5FB1")
        .orElseThrow().getChatTitle()).isEqualTo("Renamed Bob Chat");
  }

  @Test
  void updateChatTitleThrowsForUnknownChat() {
    assertThatThrownBy(() -> chatRepository
        .updateChatTitle(StringUtils.generateUlid(), "Renamed"))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void deleteRemovesSeededChat() {
    chatRepository.delete("01ARZ3NDEKTSV4RRFFQ69G5FA1");

    assertThat(chatRepository.findByChatId("01ARZ3NDEKTSV4RRFFQ69G5FA1"))
        .isEmpty();
    assertThat(chatRepository.findByChatId("01ARZ3NDEKTSV4RRFFQ69G5FA2"))
        .isPresent();
  }

  @Test
  void deleteThrowsForUnknownChat() {
    assertThatThrownBy(() -> chatRepository.delete(StringUtils.generateUlid()))
        .isInstanceOf(ChatNotFoundException.class);
  }
}
