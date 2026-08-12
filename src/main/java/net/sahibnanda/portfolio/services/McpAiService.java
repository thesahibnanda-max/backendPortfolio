package net.sahibnanda.portfolio.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.exception.GroqCallException;
import net.sahibnanda.portfolio.exception.McpCallException;
import net.sahibnanda.portfolio.mcp.McpToolClient;
import net.sahibnanda.portfolio.models.GroqCallRequest;
import net.sahibnanda.portfolio.models.GroqCallResponse;
import net.sahibnanda.portfolio.templates.PromptTemplates;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Service;

/**
 * The MCP AI: a single AI that answers the user's question directly, calling
 * this app's own MCP-exposed tools (see
 * {@link net.sahibnanda.portfolio.config.McpServerConfig}) itself, via Groq's
 * OpenAI-compatible {@code tools} field, whenever it decides it needs to --
 * unlike the Orchestrator/Worker pipeline, nothing tells it in advance which
 * domains are needed.
 *
 * <p>
 * Every Groq call this service makes -- including the one that produces the
 * final answer -- is non-streaming under the hood. Tool-calling and native
 * token streaming don't compose cleanly: the model may switch between calling a
 * tool and writing prose from one round to the next, and reconstructing partial
 * tool-call-argument JSON from streamed deltas is exactly the kind of fragile,
 * hard-to-test logic the rest of this codebase avoids. To still give SSE
 * callers the incremental-delivery experience {@link WorkerService
 * #respondStream} provides, {@link #respondStream} replays the finished answer
 * to {@code onToken} in fixed-size chunks once it's fully computed.
 */
@Slf4j
@Service
public final class McpAiService {

  /** Message role for the system prompt. */
  private static final String ROLE_SYSTEM = "system";

  /** Message role for a user turn. */
  private static final String ROLE_USER = "user";

  /** Message role for an assistant turn (including one that requests tools). */
  private static final String ROLE_ASSISTANT = "assistant";

  /** Message role for a tool's result, echoed back to the model. */
  private static final String ROLE_TOOL = "tool";

  /**
   * Maximum tool-call rounds before giving up -- bounds latency/cost against a
   * model that keeps requesting tools instead of answering.
   */
  private static final int MAX_TOOL_ROUNDS = 4;

  /** Character width of each chunk {@link #respondStream} replays. */
  private static final int STREAM_CHUNK_SIZE = 24;

  /** Client used to make the real LLM call. */
  private final LLMService llmService;

  /** Opens per-request sessions against this app's own MCP server. */
  private final McpToolClient mcpToolClient;

  /** Renders the MCP AI's system/user prompts via JTE. */
  private final PromptTemplates promptTemplates;

  /** Supplies the portfolio owner's name for the system prompt. */
  private final DetailsService detailsService;

  /**
   * Creates a new MCP AI service.
   *
   * @param llmServiceClient client used to make the real LLM call
   * @param mcpToolClientBean opens per-request sessions against this app's own
   *        MCP server
   * @param mcpPromptTemplates renders the MCP AI's system/user prompts via JTE
   * @param detailsClient supplies the portfolio owner's name for the system
   *        prompt
   */
  public McpAiService(final LLMService llmServiceClient,
      final McpToolClient mcpToolClientBean,
      final PromptTemplates mcpPromptTemplates,
      final DetailsService detailsClient) {
    this.llmService = Objects.requireNonNull(llmServiceClient,
        "llmServiceClient must not be null");
    this.mcpToolClient = Objects.requireNonNull(mcpToolClientBean,
        "mcpToolClientBean must not be null");
    this.promptTemplates = Objects.requireNonNull(mcpPromptTemplates,
        "mcpPromptTemplates must not be null");
    this.detailsService = Objects.requireNonNull(detailsClient,
        "detailsService must not be null");
  }

  /**
   * Answers the user's latest message, calling this app's own MCP tools as many
   * times as it decides it needs to (bounded by {@value #MAX_TOOL_ROUNDS}
   * rounds).
   *
   * @param mcpBaseUrl this app's own loopback base URL ({@code
   *        http://127.0.0.1:<server.port>}), built by {@code Controller} --
   *        proxy-immune since the MCP call is always self-referential
   * @param conversationHistory prior messages in the conversation, oldest first
   * @param userMessage the user's latest message
   * @return the final natural-language answer
   * @throws IllegalArgumentException if {@code userMessage} is blank
   * @throws GroqCallException if a Groq call fails
   * @throws McpCallException if opening the MCP session or listing its tools
   *         fails -- a per-tool-call failure is recovered from instead (fed
   *         back to the model as that tool's result), and exhausting
   *         {@value #MAX_TOOL_ROUNDS} rounds forces one final no-tools call for
   *         a prose answer rather than failing outright; this is only thrown
   *         for the genuinely-defensive case where that forced final call
   *         itself still requests tools
   */
  public String respond(final String mcpBaseUrl,
      final List<Message> conversationHistory, final String userMessage) {
    return converse(mcpBaseUrl, conversationHistory, userMessage);
  }

  /**
   * Answers the user's latest message like {@link #respond}, but replays the
   * finished answer to {@code onToken} in fixed-size chunks -- see this class's
   * Javadoc for why the replay happens after the answer is fully computed
   * rather than as Groq itself streams it.
   *
   * @param mcpBaseUrl this app's own loopback base URL ({@code
   *        http://127.0.0.1:<server.port>}), built by {@code Controller} --
   *        proxy-immune since the MCP call is always self-referential
   * @param conversationHistory prior messages in the conversation, oldest first
   * @param userMessage the user's latest message
   * @param onToken callback invoked once per chunk, in order
   * @return the full final natural-language answer, formed by concatenating
   *         every chunk delivered to {@code onToken}
   * @throws IllegalArgumentException if {@code userMessage} is blank
   * @throws GroqCallException if a Groq call fails
   * @throws McpCallException if opening the MCP session or listing its tools
   *         fails -- a per-tool-call failure is recovered from instead (fed
   *         back to the model as that tool's result), and exhausting
   *         {@value #MAX_TOOL_ROUNDS} rounds forces one final no-tools call for
   *         a prose answer rather than failing outright; this is only thrown
   *         for the genuinely-defensive case where that forced final call
   *         itself still requests tools
   */
  public String respondStream(final String mcpBaseUrl,
      final List<Message> conversationHistory, final String userMessage,
      final Consumer<String> onToken) {
    String answer = converse(mcpBaseUrl, conversationHistory, userMessage);
    for (String piece : chunk(answer, STREAM_CHUNK_SIZE)) {
      onToken.accept(piece);
    }
    return answer;
  }

  private String converse(final String mcpBaseUrl,
      final List<Message> conversationHistory, final String userMessage) {
    if (StringUtils.isEmpty(userMessage)) {
      throw new IllegalArgumentException("userMessage is required.");
    }

    String systemPrompt = promptTemplates
        .getSystemPromptForMcpAI(detailsService.getPortfolioOwnerName());
    String userPrompt = promptTemplates
        .getUserPromptForWorkerAI(conversationHistory, userMessage, "");

    List<GroqCallRequest.Message> messages = new ArrayList<>();
    messages.add(GroqCallRequest.Message.builder().role(ROLE_SYSTEM)
        .content(systemPrompt).build());
    messages.add(GroqCallRequest.Message.builder().role(ROLE_USER)
        .content(userPrompt).build());

    try (McpToolClient.Session session = mcpToolClient.open(mcpBaseUrl)) {
      List<GroqCallRequest.Tool> tools = session.listToolsAsGroqTools();
      log.info("Starting MCP conversation with {} tool(s) available",
          tools.size());

      for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
        GroqCallResponse.Message assistantMessage =
            callGroqAndExtractMessage(messages, tools);

        if (assistantMessage.getToolCalls() == null
            || assistantMessage.getToolCalls().isEmpty()) {
          String content = assistantMessage.getContent();
          return content == null ? "" : content;
        }

        appendToolRound(messages, session, assistantMessage, round + 1);
      }

      log.warn("Exceeded {} MCP tool-call rounds; forcing a final no-tools "
          + "call for a prose answer.", MAX_TOOL_ROUNDS);
      GroqCallResponse.Message finalMessage =
          callGroqAndExtractMessage(messages, List.of());
      if (finalMessage.getToolCalls() != null
          && !finalMessage.getToolCalls().isEmpty()) {
        throw new McpCallException("Exceeded " + MAX_TOOL_ROUNDS
            + " MCP tool-call rounds without a final answer.");
      }
      String content = finalMessage.getContent();
      return content == null ? "" : content;
    }
  }

  /**
   * Appends one round's assistant tool-request message and every tool-result
   * message to {@code messages}, mutating it in place. A per-tool-call failure
   * ({@link McpCallException} thrown by {@link McpToolClient.Session#callTool})
   * is caught and fed back to the model as that tool's result instead of
   * propagating -- the standard tool-calling recovery pattern: let the model
   * see the failure and decide how to proceed (apologize, retry a different
   * tool, or answer without that data) rather than failing the whole request.
   *
   * @param messages the running conversation, appended to in place
   * @param session the open MCP session to call tools on
   * @param assistantMessage the assistant's tool-requesting message
   * @param roundNumber the 1-based round number, used only for logging
   */
  private void appendToolRound(final List<GroqCallRequest.Message> messages,
      final McpToolClient.Session session,
      final GroqCallResponse.Message assistantMessage, final int roundNumber) {
    messages.add(GroqCallRequest.Message.builder().role(ROLE_ASSISTANT)
        .content(assistantMessage.getContent())
        .toolCalls(assistantMessage.getToolCalls()).build());

    for (GroqCallResponse.ToolCall toolCall : assistantMessage.getToolCalls()) {
      String functionName = toolCall.getFunction().getName();
      log.info("Calling MCP tool {} (round {})", functionName, roundNumber);
      String result;
      try {
        result = session.callTool(functionName,
            toolCall.getFunction().getArguments());
      } catch (McpCallException e) {
        log.warn("MCP tool {} failed; feeding the failure back to the "
            + "model: {}", functionName, e.getMessage());
        result = "Tool call failed: " + e.getMessage();
      }
      messages.add(GroqCallRequest.Message.builder().role(ROLE_TOOL)
          .toolCallId(toolCall.getId()).content(result).build());
    }
  }

  private GroqCallResponse.Message callGroqAndExtractMessage(
      final List<GroqCallRequest.Message> messages,
      final List<GroqCallRequest.Tool> tools) {
    GroqCallResponse response = llmService.callWithTools(messages, tools);
    if (response.getChoices() == null || response.getChoices().isEmpty()) {
      throw new GroqCallException("Groq response contained no choices.");
    }
    return response.getChoices().getFirst().getMessage();
  }

  /**
   * Splits {@code text} into consecutive, non-overlapping substrings of at most
   * {@code size} characters, in order -- concatenating the result always
   * exactly reproduces {@code text}.
   *
   * @param text the text to split
   * @param size the maximum width of each chunk
   * @return the chunks, in order
   */
  private static List<String> chunk(final String text, final int size) {
    List<String> chunks = new ArrayList<>();
    for (int i = 0; i < text.length(); i += size) {
      chunks.add(text.substring(i, Math.min(i + size, text.length())));
    }
    return chunks;
  }
}
