package net.sahibnanda.portfolio.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.entity.ChatEntity;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.entity.UserEntity;
import net.sahibnanda.portfolio.exception.ChatAccessDeniedException;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.exception.InvalidCredentialsException;
import net.sahibnanda.portfolio.exception.UserNotFoundException;
import net.sahibnanda.portfolio.objects.ChatObject;
import net.sahibnanda.portfolio.objects.UserObject;
import net.sahibnanda.portfolio.repository.ChatRepository;
import net.sahibnanda.portfolio.repository.UserRepository;
import net.sahibnanda.portfolio.utils.HashUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Composes {@link UserRepository} and {@link ChatRepository} into the account
 * and chat operations a controller needs: sign-up/login (with password hashing
 * via {@link HashUtils}), and chat creation, title updates, and message
 * appends, each scoped to its owning user.
 */
@Slf4j
@Service
public final class UserChatService {

  /** Repository used to create and look up user accounts. */
  private final UserRepository users;

  /** Repository used to create, look up, and update chats. */
  private final ChatRepository chats;

  /**
   * Creates a new user/chat service.
   *
   * @param userRepository repository used to create and look up user accounts
   * @param chatRepository repository used to create, look up, and update chats
   */
  public UserChatService(final UserRepository userRepository,
      final ChatRepository chatRepository) {
    this.users = Objects.requireNonNull(userRepository,
        "userRepository must not be null");
    this.chats = Objects.requireNonNull(chatRepository,
        "chatRepository must not be null");
  }

  /**
   * Registers a new user, hashing the given raw password before storing it.
   *
   * @param username the username to register
   * @param rawPassword the plain-text password to hash and store
   * @return the created user, without its password hash
   * @throws IllegalArgumentException if {@code username} or {@code rawPassword}
   *         is blank
   * @throws net.sahibnanda.portfolio.exception.DuplicateUsernameException if a
   *         user with the given username already exists
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the user cannot be persisted
   */
  public UserObject signUp(final String username, final String rawPassword) {
    requireNonBlank(username, "username");
    requireNonBlank(rawPassword, "rawPassword");

    UserEntity created = users.create(username, HashUtils.hash(rawPassword));
    return toUserObject(created);
  }

  /**
   * Authenticates a user by username and password.
   *
   * @param username the username to authenticate
   * @param rawPassword the plain-text password to check
   * @return the authenticated user, without its password hash
   * @throws IllegalArgumentException if {@code username} or {@code rawPassword}
   *         is blank
   * @throws UserNotFoundException if no user exists with the given username
   * @throws InvalidCredentialsException if the password does not match
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the lookup fails
   */
  public UserObject login(final String username, final String rawPassword) {
    requireNonBlank(username, "username");
    requireNonBlank(rawPassword, "rawPassword");

    UserEntity user = users.findByUsername(username)
        .orElseThrow(() -> new UserNotFoundException(username));
    if (!HashUtils.matches(rawPassword, user.passwordHash())) {
      throw new InvalidCredentialsException(username);
    }
    return toUserObject(user);
  }

  /**
   * Lists every chat owned by the given user.
   *
   * @param username the username whose chats are requested
   * @return every chat owned by the user, newest first, or an empty list if the
   *         user has no chats
   * @throws IllegalArgumentException if {@code username} is blank
   * @throws UserNotFoundException if no user exists with the given username
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the lookup fails
   */
  public List<ChatObject> listChats(final String username) {
    requireNonBlank(username, "username");
    if (!users.exists(username)) {
      throw new UserNotFoundException(username);
    }
    return toChatObjects(chats.findChats(username));
  }

  /**
   * Fetches a single chat owned by the given user.
   *
   * @param username the username the chat must belong to
   * @param chatId the identifier of the chat to fetch
   * @return the requested chat
   * @throws ChatNotFoundException if no chat exists with the given identifier
   * @throws ChatAccessDeniedException if the chat belongs to a different user
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the lookup fails
   */
  public ChatObject getChatById(final String username, final String chatId) {
    return toChatObject(validateOwnership(username, chatId));
  }

  /**
   * Creates a new chat for the given user.
   *
   * @param username the username of the chat owner
   * @param chatTitle the title of the new chat
   * @return every chat owned by the user, newest first, including the one just
   *         created
   * @throws IllegalArgumentException if {@code chatTitle} is blank
   * @throws UserNotFoundException if no user exists with the given username
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the chat cannot be persisted
   */
  public List<ChatObject> createChat(final String username,
      final String chatTitle) {
    requireNonBlank(chatTitle, "chatTitle");
    if (!users.exists(username)) {
      throw new UserNotFoundException(username);
    }

    chats.create(StringUtils.generateUlid(), username, chatTitle, List.of());
    return toChatObjects(chats.findChats(username));
  }

  /**
   * Renames a chat owned by the given user.
   *
   * @param username the username the chat must belong to
   * @param chatId the identifier of the chat to rename
   * @param title the new title for the chat
   * @return every chat owned by the user, newest first, reflecting the updated
   *         title
   * @throws IllegalArgumentException if {@code title} is blank
   * @throws ChatNotFoundException if no chat exists with the given identifier
   * @throws ChatAccessDeniedException if the chat belongs to a different user
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the update fails
   */
  public List<ChatObject> updateChatTitle(final String username,
      final String chatId, final String title) {
    requireNonBlank(title, "title");
    validateOwnership(username, chatId);
    chats.updateChatTitle(chatId, title);
    return toChatObjects(chats.findChats(username));
  }

  /**
   * Appends a user-authored message to a chat owned by the given user.
   *
   * @param username the username the chat must belong to
   * @param chatId the identifier of the chat to append to
   * @param content the message content
   * @return the chat, with the new message included
   * @throws IllegalArgumentException if {@code content} is blank
   * @throws ChatNotFoundException if no chat exists with the given identifier
   * @throws ChatAccessDeniedException if the chat belongs to a different user
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the update fails
   */
  public ChatObject saveUserMessage(final String username, final String chatId,
      final String content) {
    return appendMessage(username, chatId, Role.USER, content);
  }

  /**
   * Appends an assistant-authored message to a chat owned by the given user.
   *
   * @param username the username the chat must belong to
   * @param chatId the identifier of the chat to append to
   * @param content the message content
   * @return the chat, with the new message included
   * @throws IllegalArgumentException if {@code content} is blank
   * @throws ChatNotFoundException if no chat exists with the given identifier
   * @throws ChatAccessDeniedException if the chat belongs to a different user
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the update fails
   */
  public ChatObject saveAssistantMessage(final String username,
      final String chatId, final String content) {
    return appendMessage(username, chatId, Role.ASSISTANT, content);
  }

  private ChatObject appendMessage(final String username, final String chatId,
      final Role role, final String content) {
    requireNonBlank(content, "content");
    ChatEntity chat = validateOwnership(username, chatId);

    List<Message> updatedMessages = new ArrayList<>(chat.getMessages());
    updatedMessages.add(new Message(role, content, Instant.now()));

    chats.saveMessages(chatId, updatedMessages);
    return toChatObject(chats.findByChatId(chatId)
        .orElseThrow(() -> new ChatNotFoundException(chatId)));
  }

  private ChatEntity validateOwnership(final String username,
      final String chatId) {
    ChatEntity chat = chats.findByChatId(chatId)
        .orElseThrow(() -> new ChatNotFoundException(chatId));
    if (!chat.getUsername().equals(username)) {
      throw new ChatAccessDeniedException(chatId, username);
    }
    return chat;
  }

  private static void requireNonBlank(final String value,
      final String fieldName) {
    if (StringUtils.isEmpty(value)) {
      throw new IllegalArgumentException(fieldName + " is required.");
    }
  }

  private static UserObject toUserObject(final UserEntity user) {
    return UserObject.builder().username(user.username())
        .createdAt(user.createdAt()).build();
  }

  private static List<ChatObject> toChatObjects(
      final List<ChatEntity> chatEntities) {
    return chatEntities.stream().map(UserChatService::toChatObject).toList();
  }

  private static ChatObject toChatObject(final ChatEntity chat) {
    return ChatObject.builder().chatId(chat.getChatId())
        .username(chat.getUsername()).chatTitle(chat.getChatTitle())
        .messages(chat.getMessages()).createdAt(chat.getCreatedAt())
        .updatedAt(chat.getUpdatedAt()).build();
  }
}
