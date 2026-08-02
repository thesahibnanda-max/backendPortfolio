package net.sahibnanda.portfolio.exception;

public class GitHubCallException extends RuntimeException {

  public GitHubCallException(String message) {
    super(message);
  }

  public GitHubCallException(String message, Throwable cause) {
    super(message, cause);
  }
}
