package net.sahibnanda.portfolio.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.sahibnanda.portfolio.config.ChatLimitsProperties;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.objects.OrchestratorResponse;
import net.sahibnanda.portfolio.pojo.ChatRequestPOJO;
import net.sahibnanda.portfolio.pojo.ListOfChatResponsePOJO;
import net.sahibnanda.portfolio.pojo.ResponsePOJO;
import net.sahibnanda.portfolio.pojo.UserGateRequestPOJO;
import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.services.OrchestratorService;
import net.sahibnanda.portfolio.services.UserChatService;
import net.sahibnanda.portfolio.services.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies {@link Core#userPrompt} bounds the conversation history sent to the
 * Orchestrator/Worker AIs even when a chat's persisted history is larger than
 * the configured cap -- {@link OrchestratorService}/{@link WorkerService} are
 * mocked here (instead of relying on the manual-only, real-LLM tests in
 * {@link CoreTest}) so this stays covered by the ordinary test run.
 */
class CoreHistoryTruncationTest extends AbstractRepositoryIntegrationTest {

  private static final String AUTH_HEADER = "X-Auth-Token";

  @Autowired
  private Core core;

  @Autowired
  private ChatLimitsProperties chatLimits;

  @Autowired
  private UserChatService userChatService;

  @Autowired
  private ValkeyCache valkeyCache;

  @MockitoBean
  private OrchestratorService orchestratorService;

  @MockitoBean
  private WorkerService workerService;

  // createChat's global rate-limit key is shared with CoreTest/ControllerTest
  // across the whole suite -- reset it so a saturating test elsewhere doesn't
  // leak a spent budget in here.
  @BeforeEach
  void resetCreateChatRateLimit() {
    valkeyCache.delete("createChat");
  }

  @Test
  void userPromptSendsOnlyBoundedHistoryToOrchestratorAndWorker() {
    when(orchestratorService.route(anyList(), anyString()))
        .thenReturn(OrchestratorResponse.builder().requiredContexts(List.of())
            .reason("none needed").build());
    when(workerService.respond(any(), anyList(), anyString()))
        .thenReturn("canned answer");

    String token = signUpAndGetToken("tara");
    ListOfChatResponsePOJO created = (ListOfChatResponsePOJO) core.createChat(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
            .chatTitle("Long Chat").build());
    String chatId = created.getChats().get(0).getChatId();

    // Seed well more messages than maxHistoryMessages, each right at the
    // per-message char cap, so both the count bound and the char-budget
    // bound would be violated if the raw history were sent unbounded.
    int seedCount = chatLimits.maxHistoryMessages() + 10;
    String longMessage = "x".repeat(chatLimits.maxMessageLength());
    for (int i = 0; i < seedCount; i++) {
      userChatService.saveUserMessage("tara", chatId, longMessage);
    }

    ResponsePOJO response = core.userPrompt(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
            .chatId(chatId).message("latest question").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);

    ArgumentCaptor<List<Message>> historyCaptor =
        ArgumentCaptor.forClass(List.class);
    org.mockito.Mockito.verify(orchestratorService)
        .route(historyCaptor.capture(), anyString());
    List<Message> sentHistory = historyCaptor.getValue();

    assertThat(sentHistory.size())
        .isLessThanOrEqualTo(chatLimits.maxHistoryMessages());
    int totalChars =
        sentHistory.stream().mapToInt(m -> m.message().length()).sum();
    assertThat(totalChars).isLessThanOrEqualTo(chatLimits.maxHistoryChars());
  }

  private String signUpAndGetToken(final String username) {
    ResponsePOJO response = core.signUp(UserGateRequestPOJO.builder()
        .username(username).password("Str0ng!Pass").build());
    return response.getHeaders().get(AUTH_HEADER);
  }
}
