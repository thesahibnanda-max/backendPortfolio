package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.sahibnanda.portfolio.exception.McpCallException;
import net.sahibnanda.portfolio.mcp.McpToolClient;
import net.sahibnanda.portfolio.models.GroqCallRequest;
import net.sahibnanda.portfolio.models.GroqCallResponse;
import net.sahibnanda.portfolio.templates.PromptTemplates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pure-mock unit tests for {@link McpAiService}, exercising the tool-calling
 * loop against mocked constructor dependencies so no real LLM call or MCP
 * session is used.
 */
class McpAiServiceUnitTest {

  private static final String BASE_URL = "http://localhost:8080";
  private static final String OWNER_NAME = "Sahib Nanda";
  private static final String USER_MESSAGE = "What is your LeetCode rating?";

  private LLMService llmService;
  private McpToolClient mcpToolClient;
  private McpToolClient.Session session;
  private PromptTemplates promptTemplates;
  private DetailsService detailsService;
  private McpAiService mcpAiService;

  @BeforeEach
  void setup() {
    llmService = mock(LLMService.class);
    mcpToolClient = mock(McpToolClient.class);
    session = mock(McpToolClient.Session.class);
    promptTemplates = mock(PromptTemplates.class);
    detailsService = mock(DetailsService.class);
    mcpAiService = new McpAiService(llmService, mcpToolClient, promptTemplates,
        detailsService);

    when(detailsService.getPortfolioOwnerName()).thenReturn(OWNER_NAME);
    when(promptTemplates.getSystemPromptForMcpAI(OWNER_NAME))
        .thenReturn("system prompt");
    when(promptTemplates.getUserPromptForWorkerAI(any(), any(), any()))
        .thenReturn("user prompt");
    when(mcpToolClient.open(BASE_URL)).thenReturn(session);
    when(session.listToolsAsGroqTools()).thenReturn(List.of());
  }

  @Test
  void respondReturnsContentDirectlyWhenTheModelNeverCallsATool() {
    when(llmService.callWithTools(anyList(), anyList()))
        .thenReturn(finalAnswerResponse("The rating is 1800."));

    String result = mcpAiService.respond(BASE_URL, List.of(), USER_MESSAGE);

    assertThat(result).isEqualTo("The rating is 1800.");
    verify(session, times(1)).close();
  }

  @Test
  @SuppressWarnings("unchecked")
  void respondCallsTheRequestedToolThenAsksAgainForTheFinalAnswer() {
    GroqCallResponse.ToolCall toolCall = GroqCallResponse.ToolCall.builder()
        .id("call_1").type("function").function(GroqCallResponse.Function
            .builder().name("get_leetcode_details").arguments("{}").build())
        .build();
    when(llmService.callWithTools(anyList(), anyList()))
        .thenReturn(toolCallResponse(List.of(toolCall)))
        .thenReturn(finalAnswerResponse("Your rating is 1800."));
    when(session.callTool("get_leetcode_details", "{}"))
        .thenReturn("{\"rating\":1800}");

    String result = mcpAiService.respond(BASE_URL, List.of(), USER_MESSAGE);

    assertThat(result).isEqualTo("Your rating is 1800.");
    verify(session).callTool("get_leetcode_details", "{}");

    ArgumentCaptor<List<GroqCallRequest.Message>> messagesCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(llmService, times(2)).callWithTools(messagesCaptor.capture(),
        anyList());
    List<GroqCallRequest.Message> secondCallMessages =
        messagesCaptor.getAllValues().get(1);

    int assistantIndex = indexOfRoleWithToolCalls(secondCallMessages);
    int toolIndex = indexOfToolMessage(secondCallMessages, "call_1");

    assertThat(assistantIndex).isNotEqualTo(-1);
    assertThat(toolIndex).isNotEqualTo(-1);
    assertThat(assistantIndex).isLessThan(toolIndex);
    assertThat(secondCallMessages.get(toolIndex).getContent())
        .isEqualTo("{\"rating\":1800}");
  }

  @Test
  void respondThrowsAfterExceedingMaxToolRoundsWithoutAFinalAnswer() {
    GroqCallResponse.ToolCall toolCall = GroqCallResponse.ToolCall.builder()
        .id("call_1").type("function").function(GroqCallResponse.Function
            .builder().name("get_leetcode_details").arguments("{}").build())
        .build();
    when(llmService.callWithTools(anyList(), anyList()))
        .thenReturn(toolCallResponse(List.of(toolCall)));
    when(session.callTool(any(), any())).thenReturn("{}");

    assertThatThrownBy(
        () -> mcpAiService.respond(BASE_URL, List.of(), USER_MESSAGE))
        .isInstanceOf(McpCallException.class);
    verify(session, times(1)).close();
  }

  @Test
  void respondStreamReplaysTheFinalAnswerAndConcatenationMatches() {
    when(llmService.callWithTools(anyList(), anyList()))
        .thenReturn(finalAnswerResponse(
            "A longer answer with more than twenty four characters in it."));
    List<String> chunks = new ArrayList<>();
    Consumer<String> onToken = chunks::add;

    String result =
        mcpAiService.respondStream(BASE_URL, List.of(), USER_MESSAGE, onToken);

    assertThat(chunks).isNotEmpty();
    assertThat(String.join("", chunks)).isEqualTo(result);
  }

  @Test
  void respondThrowsIllegalArgumentExceptionWhenUserMessageIsBlank() {
    assertThatThrownBy(() -> mcpAiService.respond(BASE_URL, List.of(), "   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static GroqCallResponse finalAnswerResponse(final String content) {
    return GroqCallResponse.builder()
        .choices(List.of(GroqCallResponse.Choice.builder()
            .message(
                GroqCallResponse.Message.builder().content(content).build())
            .build()))
        .build();
  }

  private static GroqCallResponse toolCallResponse(
      final List<GroqCallResponse.ToolCall> toolCalls) {
    return GroqCallResponse.builder()
        .choices(List.of(GroqCallResponse.Choice.builder()
            .message(
                GroqCallResponse.Message.builder().toolCalls(toolCalls).build())
            .build()))
        .build();
  }

  private static int indexOfRoleWithToolCalls(
      final List<GroqCallRequest.Message> messages) {
    for (int i = 0; i < messages.size(); i++) {
      GroqCallRequest.Message message = messages.get(i);
      if ("assistant".equals(message.getRole())
          && message.getToolCalls() != null
          && !message.getToolCalls().isEmpty()) {
        return i;
      }
    }
    return -1;
  }

  private static int indexOfToolMessage(
      final List<GroqCallRequest.Message> messages, final String toolCallId) {
    for (int i = 0; i < messages.size(); i++) {
      GroqCallRequest.Message message = messages.get(i);
      if ("tool".equals(message.getRole())
          && toolCallId.equals(message.getToolCallId())) {
        return i;
      }
    }
    return -1;
  }
}
