package net.sahibnanda.portfolio.exception;

public class LeetcodeCallException extends RuntimeException {

  public LeetcodeCallException(String message) {
    super(message);
  }

  public LeetcodeCallException(String message, Throwable cause) {
    super(message, cause);
  }
}
