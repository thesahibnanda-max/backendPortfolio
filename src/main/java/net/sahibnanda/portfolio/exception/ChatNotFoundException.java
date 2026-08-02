package net.sahibnanda.portfolio.exception;

/**
 * Thrown when a chat with the requested identifier cannot be found in the
 * repository.
 */
public final class ChatNotFoundException extends RepositoryException {

  /**
   * Constructs a new exception for a chat that could not be found.
   *
   * @param chatId the identifier of the chat that was not found
   */
  public ChatNotFoundException(final String chatId) {
    super("Chat not found: " + chatId);
  }
}
