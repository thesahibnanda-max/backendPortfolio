package net.sahibnanda.portfolio.core;

import net.sahibnanda.portfolio.pojo.ErrorResponsePOJO;

/**
 * The result of a streaming chat handoff: either a context for processing or
 * an error. Exactly one of the two fields is non-null.
 *
 * @param context the chat stream context for processing, or {@code null} on
 *     error
 * @param error the error response, or {@code null} on success
 */
public record ChatStreamHandoff(
    ChatStreamContext context,
    ErrorResponsePOJO error) {

  /**
   * Whether this handoff represents an error.
   *
   * @return {@code true} if {@code error} is non-null, {@code false} if
   *     successful
   */
  public boolean isError() {
    return error != null;
  }
}
