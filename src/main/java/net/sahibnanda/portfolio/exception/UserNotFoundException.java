package net.sahibnanda.portfolio.exception;

public class UserNotFoundException extends RepositoryException {

  public UserNotFoundException(String username) {
    super("User not found: " + username);
  }
}
