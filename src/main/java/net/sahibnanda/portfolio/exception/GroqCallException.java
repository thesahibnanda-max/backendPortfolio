package net.sahibnanda.portfolio.exception;

public class GroqCallException extends RuntimeException {

  public GroqCallException(String message) {
    super(message);
  }

  public GroqCallException(String message, Throwable cause) {
    super(message, cause);
  }
}
