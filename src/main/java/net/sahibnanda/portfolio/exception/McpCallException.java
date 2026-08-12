package net.sahibnanda.portfolio.exception;

/**
 * Thrown when this app's self-referential MCP client fails to connect to, list
 * tools from, or call a tool on this app's own MCP server.
 */
public final class McpCallException extends RuntimeException {

  /**
   * Constructs a new exception with the given detail message.
   *
   * @param message the detail message describing the failure
   */
  public McpCallException(final String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the given detail message and underlying
   * cause.
   *
   * @param message the detail message describing the failure
   * @param cause the underlying cause of the failure
   */
  public McpCallException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
