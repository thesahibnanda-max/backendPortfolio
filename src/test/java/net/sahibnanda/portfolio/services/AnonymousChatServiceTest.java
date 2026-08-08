package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.exception.ChatAccessDeniedException;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.objects.ChatObject;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AnonymousChatServiceTest extends AbstractRepositoryIntegrationTest {

  @Autowired
  private AnonymousChatService anonymousChatService;

  @Test
  void createChatReturnsTheCreatedChat() {
    ChatObject chat =
        anonymousChatService.createChat(StringUtils.generateUlid(), "Chat 1");

    assertThat(chat.getChatId()).isNotBlank();
    assertThat(chat.getChatTitle()).isEqualTo("Chat 1");
    assertThat(chat.getUsername()).isNull();
    assertThat(chat.getMessages()).isEmpty();
  }

  @Test
  void createChatWithBlankTitleThrowsIllegalArgument() {
    assertThatThrownBy(
        () -> anonymousChatService.createChat(StringUtils.generateUlid(), " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getChatByIdReturnsTheChat() {
    String sessionId = StringUtils.generateUlid();
    ChatObject created = anonymousChatService.createChat(sessionId, "Chat 1");

    ChatObject chat =
        anonymousChatService.getChatById(sessionId, created.getChatId());

    assertThat(chat.getChatId()).isEqualTo(created.getChatId());
    assertThat(chat.getChatTitle()).isEqualTo("Chat 1");
  }

  @Test
  void getChatByIdOnUnknownChatThrowsNotFound() {
    assertThatThrownBy(() -> anonymousChatService
        .getChatById(StringUtils.generateUlid(), StringUtils.generateUlid()))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void getChatByIdByDifferentSessionThrowsAccessDenied() {
    String sessionId = StringUtils.generateUlid();
    ChatObject created = anonymousChatService.createChat(sessionId, "My Chat");

    assertThatThrownBy(() -> anonymousChatService
        .getChatById(StringUtils.generateUlid(), created.getChatId()))
        .isInstanceOf(ChatAccessDeniedException.class);
  }

  @Test
  void saveUserMessageAppendsAndReturnsChat() {
    String sessionId = StringUtils.generateUlid();
    ChatObject created = anonymousChatService.createChat(sessionId, "Chat 1");

    ChatObject chat = anonymousChatService.saveUserMessage(sessionId,
        created.getChatId(), "hello");

    assertThat(chat.getMessages()).hasSize(1);
    assertThat(chat.getMessages().get(0).message()).isEqualTo("hello");
    assertThat(chat.getMessages().get(0).role()).isEqualTo(Role.USER);
  }

  @Test
  void saveAssistantMessageAppendsAlongsideUserMessages() {
    String sessionId = StringUtils.generateUlid();
    ChatObject created = anonymousChatService.createChat(sessionId, "Chat 1");

    anonymousChatService.saveUserMessage(sessionId, created.getChatId(),
        "hello");
    ChatObject chat = anonymousChatService.saveAssistantMessage(sessionId,
        created.getChatId(), "hi there");

    assertThat(chat.getMessages()).hasSize(2);
    assertThat(chat.getMessages().get(1).message()).isEqualTo("hi there");
    assertThat(chat.getMessages().get(1).role()).isEqualTo(Role.ASSISTANT);
  }

  @Test
  void saveMessageByDifferentSessionThrowsAccessDenied() {
    String sessionId = StringUtils.generateUlid();
    ChatObject created = anonymousChatService.createChat(sessionId, "Chat 1");

    assertThatThrownBy(() -> anonymousChatService.saveUserMessage(
        StringUtils.generateUlid(), created.getChatId(), "intrusion"))
        .isInstanceOf(ChatAccessDeniedException.class);
  }
}
