package net.sahibnanda.portfolio.exception;

/**
 * Base class for exceptions raised by repository implementations when an
 * underlying data access operation fails or a requested entity cannot be
 * located.
 */
public abstract class RepositoryException extends RuntimeException {

  /**
   * Constructs a new repository exception with the given detail message.
   *
   * @param message the detail message describing the failure
   */
  protected RepositoryException(final String message) {
    super(message);
  }

  /**
   * Constructs a new repository exception with the given detail message and
   * underlying cause.
   *
   * @param message the detail message describing the failure
   * @param cause the underlying cause of the failure
   */
  protected RepositoryException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
