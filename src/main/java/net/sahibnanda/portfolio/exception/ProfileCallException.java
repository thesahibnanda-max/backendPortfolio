package net.sahibnanda.portfolio.exception;

/**
 * Thrown when a call to the Profile API fails.
 */
public final class ProfileCallException extends RuntimeException {

  /**
   * Constructs a new exception with the given detail message.
   *
   * @param message the detail message describing the failure
   */
  public ProfileCallException(final String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the given detail message and underlying
   * cause.
   *
   * @param message the detail message describing the failure
   * @param cause the underlying cause of the failure
   */
  public ProfileCallException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
