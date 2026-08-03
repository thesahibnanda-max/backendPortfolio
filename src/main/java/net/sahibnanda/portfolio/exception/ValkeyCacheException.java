package net.sahibnanda.portfolio.exception;

/**
 * Thrown when an operation against the Valkey cache fails.
 */
public final class ValkeyCacheException extends RuntimeException {

  /**
   * Constructs a new exception with the given detail message.
   *
   * @param message the detail message describing the failure
   */
  public ValkeyCacheException(final String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the given detail message and underlying
   * cause.
   *
   * @param message the detail message describing the failure
   * @param cause the underlying cause of the failure
   */
  public ValkeyCacheException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
