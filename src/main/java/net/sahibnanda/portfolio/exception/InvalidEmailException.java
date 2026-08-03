package net.sahibnanda.portfolio.exception;

/** Thrown when an email address fails to parse or validate. */
public final class InvalidEmailException extends RuntimeException {

  /**
   * Constructs a new exception for an email that failed validation.
   *
   * @param message the detail message describing the failure
   * @param cause the underlying parsing failure
   */
  public InvalidEmailException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
