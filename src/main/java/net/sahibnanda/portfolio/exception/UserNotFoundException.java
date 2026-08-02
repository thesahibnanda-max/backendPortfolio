package net.sahibnanda.portfolio.exception;

/**
 * Thrown when a user with the requested username cannot be found in the
 * repository.
 */
public final class UserNotFoundException extends RepositoryException {

  /**
   * Constructs a new exception for a user that could not be found.
   *
   * @param username the username that was not found
   */
  public UserNotFoundException(final String username) {
    super("User not found: " + username);
  }
}
