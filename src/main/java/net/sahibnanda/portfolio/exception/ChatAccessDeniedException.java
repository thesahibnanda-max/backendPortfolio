package net.sahibnanda.portfolio.exception;

/**
 * Thrown when a caller (an authenticated user or an anonymous session) attempts
 * to access or modify a chat owned by someone else.
 */
public final class ChatAccessDeniedException extends RepositoryException {

  /**
   * Constructs a new exception for a denied chat access attempt.
   *
   * @param chatId the identifier of the chat access was denied for
   * @param callerId the username or anonymous session id that attempted the
   *        access, not included in the message since either may be shown to the
   *        caller as-is
   */
  public ChatAccessDeniedException(final String chatId, final String callerId) {
    super("Access denied to chat: " + chatId);
  }
}
