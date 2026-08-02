package net.sahibnanda.portfolio.exception;

public class CodeforcesCallException extends RuntimeException {

  public CodeforcesCallException(String message) {
    super(message);
  }

  public CodeforcesCallException(String message, Throwable cause) {
    super(message, cause);
  }
}
