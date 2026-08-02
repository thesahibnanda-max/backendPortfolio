package net.sahibnanda.portfolio.exception;

public class DuplicateUsernameException extends RepositoryException {

  public DuplicateUsernameException(String username) {
    super("Username already exists: " + username);
  }
}
