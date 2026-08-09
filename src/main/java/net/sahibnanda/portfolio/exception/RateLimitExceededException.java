package net.sahibnanda.portfolio.exception;

/** Thrown when a caller exceeds a configured rate limit. */
public final class RateLimitExceededException extends RuntimeException {

  /** The window length, in seconds, the caller should wait before retrying. */
  private final long retryAfterSeconds;

  /**
   * Constructs a new exception for a rate limit that was exceeded.
   *
   * @param api the API the limit was exceeded on
   * @param ttlSeconds the rate limit window's length, in seconds
   */
  public RateLimitExceededException(final String api, final long ttlSeconds) {
    super("Rate limit exceeded for " + api + ". Please try again later.");
    this.retryAfterSeconds = ttlSeconds;
  }

  /**
   * @return the window length, in seconds, the caller should wait before
   *         retrying
   */
  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
