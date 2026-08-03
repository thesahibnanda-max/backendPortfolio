package net.sahibnanda.portfolio.exception;

/**
 * Thrown when a user attempts to access or modify a chat owned by a different
 * user.
 */
public final class ChatAccessDeniedException extends RepositoryException {

  /**
   * Constructs a new exception for a denied chat access attempt.
   *
   * @param chatId the identifier of the chat access was denied for
   * @param username the username that attempted the access
   */
  public ChatAccessDeniedException(final String chatId, final String username) {
    super("User " + username + " does not have access to chat: " + chatId);
  }
}
