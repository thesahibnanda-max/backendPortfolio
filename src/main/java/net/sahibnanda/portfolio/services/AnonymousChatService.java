package net.sahibnanda.portfolio.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.entity.AnonymousChatEntity;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.exception.ChatAccessDeniedException;
import net.sahibnanda.portfolio.exception.ChatNotFoundException;
import net.sahibnanda.portfolio.objects.ChatObject;
import net.sahibnanda.portfolio.repository.AnonymousChatRepository;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Composes {@link AnonymousChatRepository} into the ephemeral chat operations
 * an anonymous (non-logged-in) visitor needs: chat creation and message
 * appends, scoped to the visitor's session id rather than a username. There is
 * deliberately no listing, renaming, or search here -- those stay
 * authenticated-only features; see {@link UserChatService} for the equivalent
 * authenticated operations.
 */
@Slf4j
@Service
public final class AnonymousChatService {

  /** Repository used to create, look up, and update anonymous chats. */
  private final AnonymousChatRepository chats;

  /**
   * Creates a new anonymous chat service.
   *
   * @param chatRepository repository used to create, look up, and update
   *        anonymous chats
   */
  public AnonymousChatService(final AnonymousChatRepository chatRepository) {
    this.chats = Objects.requireNonNull(chatRepository,
        "chatRepository must not be null");
  }

  /**
   * Creates a new anonymous chat for the given session.
   *
   * @param sessionId the session id of the chat owner
   * @param chatTitle the title of the new chat
   * @return the created chat
   * @throws IllegalArgumentException if {@code chatTitle} is blank
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the chat cannot be persisted
   */
  public ChatObject createChat(final String sessionId, final String chatTitle) {
    requireNonBlank(chatTitle, "chatTitle");
    AnonymousChatEntity created = chats.create(StringUtils.generateUlid(),
        sessionId, chatTitle, List.of());
    return toChatObject(created);
  }

  /**
   * Fetches a single chat owned by the given session.
   *
   * @param sessionId the session id the chat must belong to
   * @param chatId the identifier of the chat to fetch
   * @return the requested chat
   * @throws ChatNotFoundException if no chat exists with the given identifier
   * @throws ChatAccessDeniedException if the chat belongs to a different
   *         session
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the lookup fails
   */
  public ChatObject getChatById(final String sessionId, final String chatId) {
    return toChatObject(validateOwnership(sessionId, chatId));
  }

  /**
   * Appends a user-authored message to a chat owned by the given session.
   *
   * @param sessionId the session id the chat must belong to
   * @param chatId the identifier of the chat to append to
   * @param content the message content
   * @return the chat, with the new message included
   * @throws IllegalArgumentException if {@code content} is blank
   * @throws ChatNotFoundException if no chat exists with the given identifier
   * @throws ChatAccessDeniedException if the chat belongs to a different
   *         session
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the update fails
   */
  public ChatObject saveUserMessage(final String sessionId, final String chatId,
      final String content) {
    return appendMessage(sessionId, chatId, Role.USER, content);
  }

  /**
   * Appends an assistant-authored message to a chat owned by the given session.
   *
   * @param sessionId the session id the chat must belong to
   * @param chatId the identifier of the chat to append to
   * @param content the message content
   * @return the chat, with the new message included
   * @throws IllegalArgumentException if {@code content} is blank
   * @throws ChatNotFoundException if no chat exists with the given identifier
   * @throws ChatAccessDeniedException if the chat belongs to a different
   *         session
   * @throws net.sahibnanda.portfolio.exception.DatabaseOperationException if
   *         the update fails
   */
  public ChatObject saveAssistantMessage(final String sessionId,
      final String chatId, final String content) {
    return appendMessage(sessionId, chatId, Role.ASSISTANT, content);
  }

  private ChatObject appendMessage(final String sessionId, final String chatId,
      final Role role, final String content) {
    requireNonBlank(content, "content");
    AnonymousChatEntity chat = validateOwnership(sessionId, chatId);

    List<Message> updatedMessages = new ArrayList<>(chat.getMessages());
    updatedMessages.add(new Message(role, content, Instant.now()));

    chats.saveMessages(chatId, updatedMessages);
    return toChatObject(chats.findByChatId(chatId)
        .orElseThrow(() -> new ChatNotFoundException(chatId)));
  }

  private AnonymousChatEntity validateOwnership(final String sessionId,
      final String chatId) {
    AnonymousChatEntity chat = chats.findByChatId(chatId)
        .orElseThrow(() -> new ChatNotFoundException(chatId));
    if (!chat.getSessionId().equals(sessionId)) {
      throw new ChatAccessDeniedException(chatId, sessionId);
    }
    return chat;
  }

  private static void requireNonBlank(final String value,
      final String fieldName) {
    if (StringUtils.isEmpty(value)) {
      throw new IllegalArgumentException(fieldName + " is required.");
    }
  }

  private static ChatObject toChatObject(final AnonymousChatEntity chat) {
    return ChatObject.builder().chatId(chat.getChatId())
        .chatTitle(chat.getChatTitle()).messages(chat.getMessages())
        .createdAt(chat.getCreatedAt()).updatedAt(chat.getUpdatedAt()).build();
  }
}
