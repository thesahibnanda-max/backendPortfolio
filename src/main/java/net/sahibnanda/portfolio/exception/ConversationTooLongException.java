package net.sahibnanda.portfolio.exception;

/**
 * Thrown when a chat has already reached the maximum number of stored messages.
 */
public final class ConversationTooLongException extends RuntimeException {

  /**
   * Constructs a new exception for a chat that reached its message cap.
   *
   * @param chatId the identifier of the chat that reached its cap
   * @param maxMessages the configured maximum number of messages per chat
   */
  public ConversationTooLongException(final String chatId,
      final int maxMessages) {
    super("This chat has reached the maximum of " + maxMessages
        + " messages. Please start a new chat.");
  }
}
