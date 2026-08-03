package net.sahibnanda.portfolio.exception;

/** Thrown when a caller exceeds a configured rate limit. */
public final class RateLimitExceededException extends RuntimeException {

  /**
   * Constructs a new exception for a rate limit that was exceeded.
   *
   * @param api the API the limit was exceeded on
   */
  public RateLimitExceededException(final String api) {
    super("Rate limit exceeded for " + api + ". Please try again later.");
  }
}
