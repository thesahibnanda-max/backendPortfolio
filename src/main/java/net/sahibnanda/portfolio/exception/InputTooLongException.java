package net.sahibnanda.portfolio.exception;

/**
 * Thrown when a caller-supplied text field exceeds its configured maximum
 * length.
 */
public final class InputTooLongException extends RuntimeException {

  /**
   * Constructs a new exception for a field that exceeded its maximum length.
   *
   * @param fieldName the name of the field that failed validation
   * @param actualLength the field's actual length
   * @param maxLength the field's configured maximum length
   */
  public InputTooLongException(final String fieldName, final int actualLength,
      final int maxLength) {
    super(fieldName + " must be at most " + maxLength + " characters (was "
        + actualLength + ").");
  }
}
