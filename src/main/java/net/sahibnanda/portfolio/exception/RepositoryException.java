package net.sahibnanda.portfolio.exception;

public abstract class RepositoryException extends RuntimeException {

  protected RepositoryException(String message) {
    super(message);
  }

  protected RepositoryException(String message, Throwable cause) {
    super(message, cause);
  }
}
