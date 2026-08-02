package net.sahibnanda.portfolio.exception;

/**
 * Thrown when a database operation performed by a repository fails
 * unexpectedly.
 */
public final class DatabaseOperationException extends RepositoryException {

  /**
   * Constructs a new exception for a failed database operation.
   *
   * @param message the detail message describing the failure
   * @param cause the underlying cause of the failure
   */
  public DatabaseOperationException(final String message,
      final Throwable cause) {
    super(message, cause);
  }
}
