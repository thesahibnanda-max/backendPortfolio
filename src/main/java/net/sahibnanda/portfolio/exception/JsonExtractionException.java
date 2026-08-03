package net.sahibnanda.portfolio.exception;

/** Thrown when no valid JSON object could be extracted from raw text. */
public final class JsonExtractionException extends RuntimeException {

  /**
   * Constructs a new exception for a JSON object that could not be extracted.
   *
   * @param message the detail message describing the failure
   */
  public JsonExtractionException(final String message) {
    super(message);
  }

  /**
   * Constructs a new exception for a JSON object that could not be extracted.
   *
   * @param message the detail message describing the failure
   * @param cause the underlying cause of the failure
   */
  public JsonExtractionException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
