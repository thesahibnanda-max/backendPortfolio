package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.exception.ChatAccessDeniedException;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.exception.DuplicateUsernameException;
import net.sahibnanda.portfolio.exception.InvalidCredentialsException;
import net.sahibnanda.portfolio.exception.UserNotFoundException;
import net.sahibnanda.portfolio.objects.ChatObject;
import net.sahibnanda.portfolio.objects.UserObject;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserChatServiceTest extends AbstractRepositoryIntegrationTest {

  @Autowired
  private UserChatService userChatService;

  @Test
  void signUpCreatesUserWithoutExposingPasswordHash() {
    UserObject created = userChatService.signUp("alice", "s3cret");

    assertThat(created.getUsername()).isEqualTo("alice");
    assertThat(created.getCreatedAt()).isNotNull();
  }

  @Test
  void signUpWithDuplicateUsernameThrows() {
    userChatService.signUp("alice", "s3cret");

    assertThatThrownBy(() -> userChatService.signUp("alice", "different"))
        .isInstanceOf(DuplicateUsernameException.class);
  }

  @Test
  void loginWithCorrectPasswordReturnsUser() {
    userChatService.signUp("alice", "s3cret");

    UserObject loggedIn = userChatService.login("alice", "s3cret");

    assertThat(loggedIn.getUsername()).isEqualTo("alice");
  }

  @Test
  void loginWithWrongPasswordThrowsInvalidCredentials() {
    userChatService.signUp("alice", "s3cret");

    assertThatThrownBy(() -> userChatService.login("alice", "wrong-password"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void loginWithUnknownUserThrowsUserNotFound() {
    assertThatThrownBy(() -> userChatService.login("nobody", "s3cret"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void createChatReturnsAllChatsForUser() {
    userChatService.signUp("alice", "s3cret");

    List<ChatObject> afterFirst = userChatService.createChat("alice", "Chat 1");
    assertThat(afterFirst).hasSize(1);

    List<ChatObject> afterSecond =
        userChatService.createChat("alice", "Chat 2");
    assertThat(afterSecond).hasSize(2);
    assertThat(afterSecond).extracting(ChatObject::getChatTitle)
        .containsExactlyInAnyOrder("Chat 1", "Chat 2");
  }

  @Test
  void createChatForUnknownUserThrowsUserNotFound() {
    assertThatThrownBy(() -> userChatService.createChat("nobody", "Chat 1"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void createChatWithBlankTitleThrowsIllegalArgument() {
    userChatService.signUp("alice", "s3cret");

    assertThatThrownBy(() -> userChatService.createChat("alice", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void listChatsReturnsAllChatsForUser() {
    userChatService.signUp("alice", "s3cret");
    userChatService.createChat("alice", "Chat 1");
    userChatService.createChat("alice", "Chat 2");

    List<ChatObject> chats = userChatService.listChats("alice");

    assertThat(chats).hasSize(2);
    assertThat(chats).extracting(ChatObject::getChatTitle)
        .containsExactlyInAnyOrder("Chat 1", "Chat 2");
  }

  @Test
  void listChatsForUnknownUserThrowsUserNotFound() {
    assertThatThrownBy(() -> userChatService.listChats("nobody"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void getChatByIdReturnsTheChat() {
    userChatService.signUp("alice", "s3cret");
    List<ChatObject> created = userChatService.createChat("alice", "Chat 1");
    String chatId = created.get(0).getChatId();

    ChatObject chat = userChatService.getChatById("alice", chatId);

    assertThat(chat.getChatId()).isEqualTo(chatId);
    assertThat(chat.getChatTitle()).isEqualTo("Chat 1");
  }

  @Test
  void getChatByIdOnUnknownChatThrowsNotFound() {
    userChatService.signUp("alice", "s3cret");

    assertThatThrownBy(
        () -> userChatService.getChatById("alice", StringUtils.generateUlid()))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void getChatByIdByNonOwnerThrowsAccessDenied() {
    userChatService.signUp("alice", "s3cret");
    userChatService.signUp("bob", "s3cret");
    List<ChatObject> aliceChats =
        userChatService.createChat("alice", "Alice's Chat");
    String chatId = aliceChats.get(0).getChatId();

    assertThatThrownBy(() -> userChatService.getChatById("bob", chatId))
        .isInstanceOf(ChatAccessDeniedException.class);
  }

  @Test
  void updateChatTitleReturnsAllChatsWithUpdatedTitle() {
    userChatService.signUp("alice", "s3cret");
    List<ChatObject> created =
        userChatService.createChat("alice", "Original Title");
    String chatId = created.get(0).getChatId();

    List<ChatObject> updated =
        userChatService.updateChatTitle("alice", chatId, "New Title");

    assertThat(updated).hasSize(1);
    assertThat(updated.get(0).getChatTitle()).isEqualTo("New Title");
  }

  @Test
  void updateChatTitleWithBlankTitleThrowsIllegalArgument() {
    userChatService.signUp("alice", "s3cret");
    List<ChatObject> created =
        userChatService.createChat("alice", "Original Title");
    String chatId = created.get(0).getChatId();

    assertThatThrownBy(
        () -> userChatService.updateChatTitle("alice", chatId, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updateChatTitleOnUnknownChatThrowsNotFound() {
    userChatService.signUp("alice", "s3cret");

    assertThatThrownBy(() -> userChatService.updateChatTitle("alice",
        StringUtils.generateUlid(), "New Title"))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void updateChatTitleByNonOwnerThrowsAccessDenied() {
    userChatService.signUp("alice", "s3cret");
    userChatService.signUp("bob", "s3cret");
    List<ChatObject> aliceChats =
        userChatService.createChat("alice", "Alice's Chat");
    String chatId = aliceChats.get(0).getChatId();

    assertThatThrownBy(
        () -> userChatService.updateChatTitle("bob", chatId, "Hijacked"))
        .isInstanceOf(ChatAccessDeniedException.class);
  }

  @Test
  void saveUserMessageAppendsAndReturnsChat() {
    userChatService.signUp("alice", "s3cret");
    List<ChatObject> created = userChatService.createChat("alice", "Chat 1");
    String chatId = created.get(0).getChatId();

    ChatObject chat = userChatService.saveUserMessage("alice", chatId, "hello");

    assertThat(chat.getMessages()).hasSize(1);
    assertThat(chat.getMessages().get(0).message()).isEqualTo("hello");
    assertThat(chat.getMessages().get(0).role()).isEqualTo(Role.USER);
  }

  @Test
  void saveAssistantMessageAppendsAlongsideUserMessages() {
    userChatService.signUp("alice", "s3cret");
    List<ChatObject> created = userChatService.createChat("alice", "Chat 1");
    String chatId = created.get(0).getChatId();

    userChatService.saveUserMessage("alice", chatId, "hello");
    ChatObject chat =
        userChatService.saveAssistantMessage("alice", chatId, "hi there");

    assertThat(chat.getMessages()).hasSize(2);
    assertThat(chat.getMessages().get(1).message()).isEqualTo("hi there");
    assertThat(chat.getMessages().get(1).role()).isEqualTo(Role.ASSISTANT);
  }

  @Test
  void saveUserMessageWithBlankContentThrowsIllegalArgument() {
    userChatService.signUp("alice", "s3cret");
    List<ChatObject> created = userChatService.createChat("alice", "Chat 1");
    String chatId = created.get(0).getChatId();

    assertThatThrownBy(
        () -> userChatService.saveUserMessage("alice", chatId, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void saveMessageOnUnknownChatThrowsNotFound() {
    userChatService.signUp("alice", "s3cret");

    assertThatThrownBy(() -> userChatService.saveUserMessage("alice",
        StringUtils.generateUlid(), "hello"))
        .isInstanceOf(ChatNotFoundException.class);
  }

  @Test
  void saveMessageByNonOwnerThrowsAccessDenied() {
    userChatService.signUp("alice", "s3cret");
    userChatService.signUp("bob", "s3cret");
    List<ChatObject> aliceChats =
        userChatService.createChat("alice", "Alice's Chat");
    String chatId = aliceChats.get(0).getChatId();

    assertThatThrownBy(
        () -> userChatService.saveUserMessage("bob", chatId, "intrusion"))
        .isInstanceOf(ChatAccessDeniedException.class);
  }
}
