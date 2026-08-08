package net.sahibnanda.portfolio.exception;

/**
 * Thrown when {@code Core} is invoked with no resolved session id on the
 * request. Should never happen in production -- {@code Controller} always
 * resolves one via the middleware package before delegating to {@code Core} --
 * so this signals a server-side invariant violation, not a client mistake.
 */
public final class SessionResolutionException extends RuntimeException {

  /**
   * Constructs a new exception for a missing resolved session id.
   *
   * @param message the detail message describing the failure
   */
  public SessionResolutionException(final String message) {
    super(message);
  }
}
