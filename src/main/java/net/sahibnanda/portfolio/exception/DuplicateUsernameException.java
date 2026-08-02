package net.sahibnanda.portfolio.exception;

/**
 * Thrown when an attempt is made to register a username that already exists in
 * the repository.
 */
public final class DuplicateUsernameException extends RepositoryException {

  /**
   * Constructs a new exception for a username that already exists.
   *
   * @param username the username that already exists
   */
  public DuplicateUsernameException(final String username) {
    super("Username already exists: " + username);
  }
}
