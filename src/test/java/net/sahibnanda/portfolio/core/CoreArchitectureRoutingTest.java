package net.sahibnanda.portfolio.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.enums.ArchitectureType;
import net.sahibnanda.portfolio.enums.ContextType;
import net.sahibnanda.portfolio.objects.ChatObject;
import net.sahibnanda.portfolio.objects.OrchestratorResponse;
import net.sahibnanda.portfolio.pojo.ChatRequestPOJO;
import net.sahibnanda.portfolio.pojo.ChatResponsePOJO;
import net.sahibnanda.portfolio.pojo.ListOfChatResponsePOJO;
import net.sahibnanda.portfolio.pojo.ResponsePOJO;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.services.McpAiService;
import net.sahibnanda.portfolio.services.OrchestratorService;
import net.sahibnanda.portfolio.services.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class CoreArchitectureRoutingTest extends AbstractRepositoryIntegrationTest {

  @Autowired
  private Core core;

  @Autowired
  private ValkeyCache valkeyCache;

  @MockitoBean
  private OrchestratorService orchestratorService;

  @MockitoBean
  private WorkerService workerService;

  @MockitoBean
  private McpAiService mcpAiService;

  @BeforeEach
  void resetRateLimits() {
    valkeyCache.delete("userPrompt");
  }

  @Test
  void defaultArchitectureUsesOrchestratorWorkerPipeline() {
    String sessionId = "session-default-arch";
    ChatObject chat = createAnonymousChat(sessionId);
    when(orchestratorService.route(anyList(), anyString()))
        .thenReturn(OrchestratorResponse.builder()
            .requiredContexts(List.of(ContextType.NONE)).reason("none needed")
            .build());
    when(workerService.respond(anyList(), anyList(), anyString()))
        .thenReturn("orchestrator-worker answer");

    ResponsePOJO response = core.userPrompt(ChatRequestPOJO.builder()
        .chatId(chat.getChatId()).message("hi").sessionId(sessionId).build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    ChatResponsePOJO chatResponse = (ChatResponsePOJO) response;
    assertThat(chatResponse.getArchitecture())
        .isEqualTo(ArchitectureType.ORCHESTRATOR_WORKER);
    verify(orchestratorService).route(anyList(), anyString());
    verifyNoInteractions(mcpAiService);
  }

  @Test
  void mcpArchitectureSkipsOrchestratorAndCallsMcpAiService() {
    String sessionId = "session-mcp-arch";
    ChatObject chat = createAnonymousChat(sessionId);
    when(mcpAiService.respond(anyString(), anyList(), anyString()))
        .thenReturn("mcp answer");

    ResponsePOJO response = core.userPrompt(
        ChatRequestPOJO.builder().chatId(chat.getChatId()).message("hi")
            .sessionId(sessionId).architecture(ArchitectureType.MCP)
            .mcpBaseUrl("http://localhost:8080").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    ChatResponsePOJO chatResponse = (ChatResponsePOJO) response;
    assertThat(chatResponse.getChat().getMessages().getLast().message())
        .isEqualTo("mcp answer");
    assertThat(chatResponse.getArchitecture()).isEqualTo(ArchitectureType.MCP);
    verifyNoInteractions(orchestratorService, workerService);
  }

  @Test
  void defaultArchitecturePrepareUserPromptStreamCallsOrchestrator() {
    String sessionId = "stream-default-arch";
    ChatObject chat = createAnonymousChat(sessionId);
    when(orchestratorService.route(anyList(), anyString()))
        .thenReturn(OrchestratorResponse.builder()
            .requiredContexts(List.of(ContextType.NONE)).reason("none needed")
            .build());

    ChatStreamHandoff handoff = core.prepareUserPromptStream(
        ChatRequestPOJO.builder().chatId(chat.getChatId()).message("hi")
            .sessionId(sessionId).build());

    assertThat(handoff.isError()).isFalse();
    assertThat(handoff.context().architecture())
        .isEqualTo(ArchitectureType.ORCHESTRATOR_WORKER);
    verify(orchestratorService).route(anyList(), anyString());
  }

  @Test
  void mcpArchitecturePrepareUserPromptStreamSkipsOrchestrator() {
    String sessionId = "stream-mcp-arch";
    ChatObject chat = createAnonymousChat(sessionId);

    ChatStreamHandoff handoff = core.prepareUserPromptStream(
        ChatRequestPOJO.builder().chatId(chat.getChatId()).message("hi")
            .sessionId(sessionId).architecture(ArchitectureType.MCP)
            .mcpBaseUrl("http://localhost:8080").build());

    assertThat(handoff.isError()).isFalse();
    ChatStreamContext context = handoff.context();
    assertThat(context.architecture()).isEqualTo(ArchitectureType.MCP);
    assertThat(context.requiredContexts()).isEmpty();
    assertThat(context.mcpBaseUrl()).isEqualTo("http://localhost:8080");
    verifyNoInteractions(orchestratorService);
  }

  private ChatObject createAnonymousChat(final String sessionId) {
    ResponsePOJO created = core.createChat(ChatRequestPOJO.builder()
        .chatTitle("Architecture routing test").sessionId(sessionId).build());
    return ((ListOfChatResponsePOJO) created).getChats().get(0);
  }
}
