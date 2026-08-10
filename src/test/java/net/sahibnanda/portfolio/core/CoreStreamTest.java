package net.sahibnanda.portfolio.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.config.ChatLimitsProperties;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.enums.ContextType;
import net.sahibnanda.portfolio.exception.GroqCallException;
import net.sahibnanda.portfolio.objects.ChatObject;
import net.sahibnanda.portfolio.objects.OrchestratorResponse;
import net.sahibnanda.portfolio.pojo.ChatRequestPOJO;
import net.sahibnanda.portfolio.pojo.ChatStreamDonePOJO;
import net.sahibnanda.portfolio.pojo.ChatStreamTokenPOJO;
import net.sahibnanda.portfolio.pojo.ErrorResponsePOJO;
import net.sahibnanda.portfolio.pojo.ListOfChatResponsePOJO;
import net.sahibnanda.portfolio.pojo.UserGateRequestPOJO;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.services.OrchestratorService;
import net.sahibnanda.portfolio.services.UserChatService;
import net.sahibnanda.portfolio.services.WorkerService;
import net.sahibnanda.portfolio.utils.JsonUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Verifies {@link Core#prepareUserPromptStream} and
 * {@link Core#streamWorkerAnswer}, the streaming counterparts to
 * {@link Core#userPrompt} -- {@link OrchestratorService}/{@link WorkerService}
 * are mocked here (same pattern as {@link CoreHistoryTruncationTest}) so this
 * stays deterministic and covered by the ordinary test run, without needing a
 * real Groq key.
 */
class CoreStreamTest extends AbstractRepositoryIntegrationTest {

  private static final String AUTH_HEADER = "X-Auth-Token";

  private static final String UPSTREAM_ERROR_MESSAGE =
      "A downstream service is currently unavailable. Please try again "
          + "later.";

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

  // userPrompt's rate-limit keys are shared (deliberately, per the streaming
  // feature's design) with prepareUserPromptStream, and live in the real,
  // shared Valkey instance -- reset them so a saturating test elsewhere
  // (e.g. CoreTest's userPrompt rate-limit tests) doesn't leak into this
  // class, whose ordering relative to those isn't guaranteed.
  @BeforeEach
  void resetRateLimits() {
    valkeyCache.delete("createChat");
    valkeyCache.delete("userPrompt");
  }

  @Test
  void prepareUserPromptStreamExceedingRateLimitReturnsTooManyRequests() {
    valkeyCache.delete("userPrompt:uma");
    String token = signUpAndGetToken("uma");

    ChatStreamHandoff handoff = null;
    for (int i = 0; i < 6; i++) {
      handoff = core.prepareUserPromptStream(
          ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
              .chatId(StringUtils.generateUlid()).message("Hi").build());
    }

    assertThat(handoff.isError()).isTrue();
    assertThat(handoff.error().getHttpStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void prepareUserPromptStreamForUnknownChatReturnsNotFoundError() {
    valkeyCache.delete("userPrompt:vera");
    String token = signUpAndGetToken("vera");

    ChatStreamHandoff handoff = core.prepareUserPromptStream(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
            .chatId(StringUtils.generateUlid()).message("Hi").build());

    assertThat(handoff.isError()).isTrue();
    assertThat(handoff.error().getHttpStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void prepareUserPromptStreamWithTooLongMessageReturnsBadRequestError() {
    valkeyCache.delete("userPrompt:wade");
    String token = signUpAndGetToken("wade");
    String chatId = createChatId(token, "AI Chat");
    String tooLongMessage = "x".repeat(chatLimits.maxMessageLength() + 1);

    ChatStreamHandoff handoff = core.prepareUserPromptStream(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
            .chatId(chatId).message(tooLongMessage).build());

    assertThat(handoff.isError()).isTrue();
    assertThat(handoff.error().getHttpStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void prepareUserPromptStreamExceedingPerChatMessageCapReturnsBadRequestError() {
    valkeyCache.delete("userPrompt:xena");
    String token = signUpAndGetToken("xena");
    String chatId = createChatId(token, "Long Chat");
    for (int i = 0; i < chatLimits.maxMessagesPerChat(); i++) {
      userChatService.saveUserMessage("xena", chatId, "msg " + i);
    }

    ChatStreamHandoff handoff = core.prepareUserPromptStream(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
            .chatId(chatId).message("one more").build());

    assertThat(handoff.isError()).isTrue();
    assertThat(handoff.error().getHttpStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void prepareUserPromptStreamOnSuccessReturnsContextAndNeverCallsWorker() {
    valkeyCache.delete("userPrompt:yara");
    when(orchestratorService.route(anyList(), anyString()))
        .thenReturn(OrchestratorResponse.builder()
            .requiredContexts(List.of(ContextType.PROFILE))
            .reason("needs resume context").build());
    String token = signUpAndGetToken("yara");
    String chatId = createChatId(token, "AI Chat");

    ChatStreamHandoff handoff = core.prepareUserPromptStream(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
            .chatId(chatId).message("Hello there").build());

    assertThat(handoff.isError()).isFalse();
    ChatStreamContext context = handoff.context();
    assertThat(context.authenticated()).isTrue();
    assertThat(context.callerId()).isEqualTo("yara");
    assertThat(context.chatId()).isEqualTo(chatId);
    assertThat(context.userMessage()).isEqualTo("Hello there");
    assertThat(context.requiredContexts()).containsExactly(ContextType.PROFILE);
    assertThat(context.boundedHistory()).isEmpty();
    verifyNoInteractions(workerService);
  }

  @Test
  void streamWorkerAnswerOnSuccessSendsTokensThenDoneAndPersistsBothMessages()
      throws IOException {
    String token = signUpAndGetToken("zane");
    String chatId = createChatId(token, "AI Chat");
    doAnswer(invocation -> {
      Consumer<String> onToken = invocation.getArgument(3);
      onToken.accept("Hello");
      onToken.accept(", world");
      onToken.accept("!");
      return "Hello, world!";
    }).when(workerService).respondStream(any(), anyList(), anyString(), any());

    ChatStreamContext context = new ChatStreamContext(true, "zane", chatId,
        "Hi there", List.of(), List.of());
    SseEmitter emitter = mock(SseEmitter.class);

    core.streamWorkerAnswer(context, emitter);

    ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
        ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
    verify(emitter, times(4)).send(captor.capture());
    verify(emitter, times(1)).complete();
    List<SseEmitter.SseEventBuilder> sent = captor.getAllValues();

    assertSseEvent(sent.get(0), "token",
        ChatStreamTokenPOJO.builder().content("Hello").build());
    assertSseEvent(sent.get(1), "token",
        ChatStreamTokenPOJO.builder().content(", world").build());
    assertSseEvent(sent.get(2), "token",
        ChatStreamTokenPOJO.builder().content("!").build());

    ChatObject chat = userChatService.getChatById("zane", chatId);
    assertThat(chat.getMessages()).hasSize(2);
    assertThat(chat.getMessages().get(0).message()).isEqualTo("Hi there");
    assertThat(chat.getMessages().get(1).message()).isEqualTo("Hello, world!");
    Message lastMessage = chat.getMessages().get(1);

    assertSseEvent(sent.get(3), "done", ChatStreamDonePOJO.builder()
        .message("Hello, world!").timestamp(lastMessage.timestamp()).build());
  }

  @Test
  void streamWorkerAnswerOnGroqFailureSendsErrorEventAndPersistsNothing()
      throws IOException {
    String token = signUpAndGetToken("yusuf");
    String chatId = createChatId(token, "AI Chat");
    when(workerService.respondStream(any(), anyList(), anyString(), any()))
        .thenThrow(new GroqCallException("upstream boom"));

    ChatStreamContext context = new ChatStreamContext(true, "yusuf", chatId,
        "Hi there", List.of(), List.of());
    SseEmitter emitter = mock(SseEmitter.class);

    core.streamWorkerAnswer(context, emitter);

    ArgumentCaptor<SseEmitter.SseEventBuilder> captor =
        ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
    verify(emitter, times(1)).send(captor.capture());
    verify(emitter, times(1)).complete();

    assertSseEvent(captor.getValue(), "error", ErrorResponsePOJO.builder()
        .showMessageAsIs(false).errorMessage(UPSTREAM_ERROR_MESSAGE).build());

    ChatObject chat = userChatService.getChatById("yusuf", chatId);
    assertThat(chat.getMessages()).isEmpty();
  }

  /**
   * Asserts that a captured {@link SseEmitter.SseEventBuilder} argument carries
   * exactly the given SSE event name and JSON-serialized payload.
   *
   * <p>
   * {@code SseEventBuilder} is a builder that Spring's real {@code
   * SseEmitter.send(...)} consumes internally via its ({@code public}) {@code
   * build()} method -- since {@code emitter} itself is a Mockito mock here,
   * {@code send(...)} never actually runs, so the captured builder is still
   * fully intact and {@code build()} can safely be called again in the test.
   * {@code build()} returns a {@code Set} of {@code DataWithMediaType}
   * fragments: Spring's {@code SseEventBuilderImpl} always emits the {@code
   * "event:<name>\n"} header (plus a trailing newline) as separate {@code
   * text/plain} fragments, and -- because {@code Core.sendEvent} passes an
   * already-JSON-serialized {@code String} with an explicit {@code
   * application/json} {@link MediaType} to {@code .data(...)} -- a single-line
   * JSON string is routed straight into its own {@code application/json}
   * fragment untouched (verified by decompiling {@code SseEventBuilderImpl}'s
   * bytecode: a data string with no embedded newline bypasses the
   * text-buffering path entirely). That lets this assertion recover the exact
   * event name and exact JSON payload Core sent, rather than falling back to
   * invocation-count/ordering-only checks.
   */
  private void assertSseEvent(final SseEmitter.SseEventBuilder builder,
      final String expectedEventName, final Object expectedPayload) {
    Set<ResponseBodyEmitter.DataWithMediaType> parts = builder.build();

    // SseEventBuilderImpl writes its "event:<name>\n"/"data:" control text
    // using its own SseEmitter.TEXT_PLAIN constant (text/plain with an
    // explicit UTF-8 charset param) -- NOT the parameterless
    // MediaType.TEXT_PLAIN constant, so those two MediaType instances are
    // not equal(). Matching "everything that isn't our application/json
    // payload" sidesteps that without depending on package-private access.
    String header = parts.stream()
        .filter(part -> !MediaType.APPLICATION_JSON.equals(part.getMediaType()))
        .map(part -> (String) part.getData()).collect(Collectors.joining());
    assertThat(header).contains("event:" + expectedEventName + "\ndata:");

    String payloadJson = parts.stream()
        .filter(part -> MediaType.APPLICATION_JSON.equals(part.getMediaType()))
        .map(part -> (String) part.getData()).findFirst().orElseThrow(
            () -> new AssertionError("No JSON payload found in SSE event"));
    assertThat(payloadJson).isEqualTo(JsonUtils.toJson(expectedPayload));
  }

  private String createChatId(final String token, final String title) {
    ListOfChatResponsePOJO created =
        (ListOfChatResponsePOJO) core.createChat(ChatRequestPOJO.builder()
            .headers(Map.of(AUTH_HEADER, token)).chatTitle(title).build());
    return created.getChats().get(0).getChatId();
  }

  private String signUpAndGetToken(final String username) {
    var response = core.signUp(UserGateRequestPOJO.builder().username(username)
        .password("Str0ng!Pass").build());
    return response.getHeaders().get(AUTH_HEADER);
  }
}
