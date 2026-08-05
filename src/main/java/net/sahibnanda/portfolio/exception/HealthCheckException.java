package net.sahibnanda.portfolio.exception;

import java.util.List;

/**
 * Thrown when one or more infrastructure dependency pings fail.
 */
public final class HealthCheckException extends RuntimeException {

  /**
   * Constructs a new exception naming every dependency that failed to respond.
   *
   * @param failedDependencies the names of the dependencies that failed to
   *        respond
   */
  public HealthCheckException(final List<String> failedDependencies) {
    super("Health check failed for: " + String.join(", ", failedDependencies));
  }
}
