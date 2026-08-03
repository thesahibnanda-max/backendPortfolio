package net.sahibnanda.portfolio.exception;

/**
 * Thrown when writing to or deleting from the two-tier cache fails.
 */
public final class CacheSetException extends RuntimeException {

  /**
   * Constructs a new exception with the given detail message.
   *
   * @param message the detail message describing the failure
   */
  public CacheSetException(final String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the given detail message and underlying
   * cause.
   *
   * @param message the detail message describing the failure
   * @param cause the underlying cause of the failure
   */
  public CacheSetException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
