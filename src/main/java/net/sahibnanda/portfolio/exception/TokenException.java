package net.sahibnanda.portfolio.exception;

/** Thrown when an auth token cannot be encrypted or decrypted. */
public final class TokenException extends RuntimeException {

  /**
   * Constructs a new exception for a token operation that failed.
   *
   * @param message the detail message describing the failure
   * @param cause the underlying cause of the failure
   */
  public TokenException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
