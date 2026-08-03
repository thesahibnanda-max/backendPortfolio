package net.sahibnanda.portfolio.exception;

/**
 * Thrown when a login attempt supplies an incorrect password for a known
 * username.
 */
public final class InvalidCredentialsException extends RuntimeException {

  /**
   * Constructs a new exception for a failed login attempt.
   *
   * @param username the username the incorrect password was supplied for
   */
  public InvalidCredentialsException(final String username) {
    super("Invalid credentials for user: " + username);
  }
}
