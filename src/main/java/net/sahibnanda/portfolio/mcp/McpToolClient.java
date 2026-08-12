package net.sahibnanda.portfolio.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.sahibnanda.portfolio.exception.McpCallException;
import net.sahibnanda.portfolio.models.GroqCallRequest;
import net.sahibnanda.portfolio.utils.JsonUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Opens a short-lived MCP client session against this app's own MCP server (see
 * {@code spring.ai.mcp.server.*} in application.yml, and
 * {@link net.sahibnanda.portfolio.config.McpServerConfig}), reached at a
 * loopback base URL built by {@code Controller} from this app's own configured
 * port ({@code http://127.0.0.1:<server.port>}) rather than anything derived
 * from the caller's request -- this call is always self-referential, so a
 * loopback address is both correct and immune to a reverse proxy (e.g. Caddy)
 * sitting in front of the app with no {@code server.forward-headers-strategy}
 * configured.
 */
@Component
public final class McpToolClient {

  /**
   * The MCP endpoint path this server listens on -- must match {@code
   * spring.ai.mcp.server.streamable-http.mcp-endpoint} in application.yml
   * (Spring's own default, also used here). If this ever drifts out of sync
   * with that config value, {@code McpToolClientIntegrationTest} fails
   * immediately, so this hardcoded literal is safely guarded rather than being
   * a silent risk.
   */
  private static final String MCP_ENDPOINT_PATH = "/mcp";

  /** The tool "type" every MCP tool is translated into for Groq. */
  private static final String GROQ_TOOL_TYPE = "function";

  /**
   * Opens a new MCP client session against {@code baseUrl}.
   *
   * @param baseUrl the scheme+host+port to reach this app's own MCP server at
   *        (e.g. {@code http://localhost:8080})
   * @return an open session; callers must close it (try-with-resources) once
   *         done
   * @throws IllegalArgumentException if {@code baseUrl} is blank
   * @throws McpCallException if the MCP handshake fails
   */
  public Session open(final String baseUrl) {
    if (StringUtils.isEmpty(baseUrl)) {
      throw new IllegalArgumentException("baseUrl is required.");
    }

    HttpClientStreamableHttpTransport transport =
        HttpClientStreamableHttpTransport.builder(baseUrl)
            .endpoint(MCP_ENDPOINT_PATH).build();
    McpSyncClient client = McpClient.sync(transport).build();
    try {
      client.initialize();
    } catch (RuntimeException e) {
      client.closeGracefully();
      throw new McpCallException(
          "Failed to initialize MCP client for " + baseUrl, e);
    }
    return new Session(client);
  }

  /**
   * One open MCP client session: lists the tools this app's own MCP server
   * exposes (translated into Groq's tool-calling schema) and calls them by
   * name.
   */
  public static final class Session implements AutoCloseable {

    /** The underlying MCP sync client this session wraps. */
    private final McpSyncClient client;

    private Session(final McpSyncClient mcpSyncClient) {
      this.client = mcpSyncClient;
    }

    /**
     * Lists every tool this app's MCP server currently exposes, translated into
     * Groq's OpenAI-compatible tool-calling schema.
     *
     * @return the available tools, translated for Groq
     * @throws McpCallException if listing tools fails
     */
    public List<GroqCallRequest.Tool> listToolsAsGroqTools() {
      McpSchema.ListToolsResult result;
      try {
        result = client.listTools();
      } catch (RuntimeException e) {
        throw new McpCallException("Failed to list MCP tools.", e);
      }
      return result.tools().stream().map(Session::toGroqTool).toList();
    }

    /**
     * Calls a tool by name with the given JSON-encoded arguments.
     *
     * @param name the tool's name, as returned by
     *        {@link #listToolsAsGroqTools()}
     * @param jsonArguments the arguments to call it with, JSON-encoded (as
     *        Groq's {@code function.arguments} delivers them)
     * @return the tool's result, as plain text
     * @throws McpCallException if the call fails, or the server reports an
     *         error result
     */
    public String callTool(final String name, final String jsonArguments) {
      Map<String, Object> arguments =
          StringUtils.isEmpty(jsonArguments) ? Map.of()
              : JsonUtils.fromJson(jsonArguments,
                  new TypeReference<Map<String, Object>>() {
                  });
      McpSchema.CallToolRequest request =
          McpSchema.CallToolRequest.builder(name).arguments(arguments).build();

      McpSchema.CallToolResult result;
      try {
        result = client.callTool(request);
      } catch (RuntimeException e) {
        throw new McpCallException("Failed to call MCP tool " + name, e);
      }

      String text = extractText(result);
      if (Boolean.TRUE.equals(result.isError())) {
        throw new McpCallException(
            "MCP tool " + name + " returned an error: " + text);
      }
      return text;
    }

    @Override
    public void close() {
      client.closeGracefully();
    }

    private static GroqCallRequest.Tool toGroqTool(final McpSchema.Tool tool) {
      return GroqCallRequest.Tool.builder().type(GROQ_TOOL_TYPE)
          .function(GroqCallRequest.ToolFunction.builder().name(tool.name())
              .description(tool.description()).parameters(tool.inputSchema())
              .build())
          .build();
    }

    private static String extractText(final McpSchema.CallToolResult result) {
      if (result.content() == null) {
        return "";
      }
      return result.content().stream()
          .filter(McpSchema.TextContent.class::isInstance)
          .map(McpSchema.TextContent.class::cast)
          .map(McpSchema.TextContent::text).collect(Collectors.joining("\n"));
    }
  }
}
