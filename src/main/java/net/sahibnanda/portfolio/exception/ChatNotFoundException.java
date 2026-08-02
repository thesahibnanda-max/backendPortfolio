package net.sahibnanda.portfolio.exception;

public class ChatNotFoundException extends RepositoryException {

  public ChatNotFoundException(String chatId) {
    super("Chat not found: " + chatId);
  }
}
