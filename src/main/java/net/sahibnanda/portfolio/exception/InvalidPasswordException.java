package net.sahibnanda.portfolio.exception;

/** Thrown when a password fails the configured strength rules. */
public final class InvalidPasswordException extends RuntimeException {

  /**
   * Constructs a new exception for a password that failed validation.
   *
   * @param message the detail message describing which rule failed
   */
  public InvalidPasswordException(final String message) {
    super(message);
  }
}
