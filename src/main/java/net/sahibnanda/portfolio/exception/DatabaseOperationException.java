package net.sahibnanda.portfolio.exception;

public class DatabaseOperationException extends RepositoryException {

  public DatabaseOperationException(String message, Throwable cause) {
    super(message, cause);
  }
}
