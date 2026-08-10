package net.sahibnanda.portfolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.function.Consumer;
import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.config.ChatLimitsProperties;
import net.sahibnanda.portfolio.objects.OrchestratorResponse;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.services.OrchestratorService;
import net.sahibnanda.portfolio.services.WorkerService;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class ControllerTest extends AbstractRepositoryIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ValkeyCache valkeyCache;

  @Autowired
  private ChatLimitsProperties chatLimits;

  @MockitoBean
  private OrchestratorService orchestratorService;

  @MockitoBean
  private WorkerService workerService;

  // createChat's global rate-limit key is deliberately saturated by
  // createChatExceedingRateLimitReturns429WithRetryAfterHeader below --
  // reset it before every test so that doesn't leak a spent budget into
  // whichever test happens to run next in the same 60s window.
  //
  // userPromptStream shares its rate-limit key ("userPrompt") with
  // Core#userPrompt, and that key lives in the same shared Valkey instance
  // used by every other test class in the suite (e.g. CoreTest's
  // userPrompt rate-limit test deliberately exhausts its budget) -- reset
  // it too so this class's userPromptStream tests always start fresh.
  @BeforeEach
  void resetRateLimits() {
    valkeyCache.delete("createChat");
    valkeyCache.delete("createChat:ip:127.0.0.1");
    valkeyCache.delete("userPrompt");
  }

  @Test
  void signUpReturnsCreatedWithAuthTokenHeader() throws Exception {
    mockMvc
        .perform(
            post("/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"mia","password":"Str0ng!Pass"}"""))
        .andExpect(status().isCreated())
        .andExpect(header().exists("X-Auth-Token"));
  }

  @Test
  void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
    mockMvc.perform(
        post("/signup").contentType(MediaType.APPLICATION_JSON).content("""
            {"username":"noah","password":"Str0ng!Pass"}"""));

    mockMvc
        .perform(
            post("/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"noah","password":"Wrong!Pass1"}"""))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createChatWithoutAuthTokenCreatesAnonymousChatAndSetsSessionIdHeader()
      throws Exception {
    mockMvc
        .perform(
            post("/chats").contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"My Chat"}"""))
        .andExpect(status().isCreated())
        .andExpect(header().exists("X-Session-Id"))
        .andExpect(jsonPath("$.chats", hasSize(1)))
        .andExpect(jsonPath("$.chats[0].chatTitle").value("My Chat"));
  }

  @Test
  void firstRequestWithNoSessionIdHeaderSetsOneOnTheResponse()
      throws Exception {
    mockMvc.perform(get("/health")).andExpect(status().isOk())
        .andExpect(header().exists("X-Session-Id"));
  }

  @Test
  void anonymousSessionIdHeaderPersistsAcrossFollowUpRequests()
      throws Exception {
    MvcResult createResult = mockMvc
        .perform(
            post("/chats").contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"Anon Chat"}"""))
        .andExpect(status().isCreated()).andReturn();
    String sessionId = createResult.getResponse().getHeader("X-Session-Id");
    String chatId = new ObjectMapper()
        .readTree(createResult.getResponse().getContentAsString()).get("chats")
        .get(0).get("chatId").asText();

    // Replaying the same session id reaches the same anonymous chat.
    mockMvc.perform(get("/chats/" + chatId).header("X-Session-Id", sessionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chat.chatTitle").value("Anon Chat"));

    // No session id header at all means a brand-new session, which doesn't
    // own the chat -- ChatAccessDeniedException maps to 403, not 401 or
    // 404, since the chat does exist.
    mockMvc.perform(get("/chats/" + chatId)).andExpect(status().isForbidden());
  }

  @Test
  void anonymousSessionIdHeaderDoesNotUnlockAuthenticatedOnlyEndpoints()
      throws Exception {
    MvcResult createResult = mockMvc
        .perform(
            post("/chats").contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"Anon Chat"}"""))
        .andExpect(status().isCreated()).andReturn();
    String sessionId = createResult.getResponse().getHeader("X-Session-Id");
    String chatId = new ObjectMapper()
        .readTree(createResult.getResponse().getContentAsString()).get("chats")
        .get(0).get("chatId").asText();

    mockMvc.perform(get("/chats").header("X-Session-Id", sessionId))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(post("/chats/search").header("X-Session-Id", sessionId)
        .contentType(MediaType.APPLICATION_JSON).content("""
            {"query":"anything"}""")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(patch("/chats/" + chatId).header("X-Session-Id", sessionId)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"Renamed"}"""))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createChatWithWrongJsonKeyCasingReturnsBadRequest() throws Exception {
    String signUpBody = mockMvc
        .perform(
            post("/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"quinn","password":"Str0ng!Pass"}"""))
        .andReturn().getResponse().getHeader("X-Auth-Token");

    // "chat_title" (snake_case) doesn't match the "chatTitle" property, so
    // it's silently dropped rather than deserialized -- this should be
    // caught as a validation error, not reach the database as a null.
    mockMvc
        .perform(post("/chats").header("X-Auth-Token", signUpBody)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"chat_title":"Chat Number 1"}"""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createChatThenListThenGetThenRenameRoundTrips() throws Exception {
    String signUpBody = mockMvc
        .perform(
            post("/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"olivia","password":"Str0ng!Pass"}"""))
        .andReturn().getResponse().getHeader("X-Auth-Token");

    mockMvc
        .perform(post("/chats").header("X-Auth-Token", signUpBody)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"My Chat"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.chats", hasSize(1)));

    String listResponse =
        mockMvc.perform(get("/chats").header("X-Auth-Token", signUpBody))
            .andExpect(status().isOk()).andReturn().getResponse()
            .getContentAsString();
    JsonNode chats = new ObjectMapper().readTree(listResponse).get("chats");
    String chatId = chats.get(0).get("chatId").asText();

    mockMvc.perform(get("/chats/" + chatId).header("X-Auth-Token", signUpBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chat.chatTitle").value("My Chat"));

    mockMvc
        .perform(patch("/chats/" + chatId).header("X-Auth-Token", signUpBody)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"Renamed"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chat.chatTitle").value("Renamed"));
  }

  @Test
  void searchChatWithoutAuthTokenReturnsUnauthorized() throws Exception {
    mockMvc.perform(post("/chats/search")
        .contentType(MediaType.APPLICATION_JSON).content("""
            {"query":"anything"}""")).andExpect(status().isUnauthorized());
  }

  @Test
  void searchChatReturnsChatsAndScores() throws Exception {
    // searchChat's rate-limit keys, like userPrompt's, live in the shared
    // Valkey instance and aren't reset between test runs -- clear them so
    // this test's budget always starts fresh.
    valkeyCache.delete("searchChat");
    valkeyCache.delete("searchChat:parker");
    String signUpBody = mockMvc
        .perform(
            post("/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"parker","password":"Str0ng!Pass"}"""))
        .andReturn().getResponse().getHeader("X-Auth-Token");
    mockMvc
        .perform(post("/chats").header("X-Auth-Token", signUpBody)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"Kyoto travel itinerary"}"""))
        .andExpect(status().isCreated());

    JsonNode response =
        awaitSearchResponse(signUpBody, "kyoto travel itinerary");

    String chatId = response.get("chats").get(0).get("chatId").asText();
    assertThat(response.get("scores").has(chatId)).isTrue();
  }

  @Test
  void userPromptStreamForUnknownChatReturnsNotFoundJsonWithoutOpeningSse()
      throws Exception {
    valkeyCache.delete("userPrompt:aiden");
    String signUpBody = mockMvc
        .perform(
            post("/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"aiden","password":"Str0ng!Pass"}"""))
        .andReturn().getResponse().getHeader("X-Auth-Token");

    mockMvc
        .perform(
            post("/chats/" + StringUtils.generateUlid() + "/messages/stream")
                .header("X-Auth-Token", signUpBody)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"message":"Hello"}"""))
        .andExpect(request().asyncNotStarted()).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorMessage").exists());
  }

  @Test
  void userPromptStreamWithOverLongMessageReturnsBadRequest() throws Exception {
    valkeyCache.delete("userPrompt:brooke");
    String signUpBody = mockMvc
        .perform(
            post("/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"brooke","password":"Str0ng!Pass"}"""))
        .andReturn().getResponse().getHeader("X-Auth-Token");
    String createResponse = mockMvc
        .perform(post("/chats").header("X-Auth-Token", signUpBody)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"AI Chat"}"""))
        .andExpect(status().isCreated()).andReturn().getResponse()
        .getContentAsString();
    String chatId = new ObjectMapper().readTree(createResponse).get("chats")
        .get(0).get("chatId").asText();
    String tooLongMessage = "x".repeat(chatLimits.maxMessageLength() + 1);

    mockMvc
        .perform(post("/chats/" + chatId + "/messages/stream")
            .header("X-Auth-Token", signUpBody)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"" + tooLongMessage + "\"}"))
        .andExpect(status().isBadRequest());
  }

  // Verifies the full SSE round trip through real MockMvc async dispatch,
  // rather than falling back to mocking Core itself: Spring MVC Test's
  // documented pattern for async controller methods is perform() ->
  // assert request().asyncStarted() -> asyncDispatch(result) on a second
  // perform() once the async processing has completed. Since
  // streamWorkerAnswer runs on a separate virtual thread started by the
  // controller, "completed" can't be assumed the instant the first
  // perform() returns -- MvcResult#getAsyncResult(timeoutMillis) is the
  // supported blocking wait for that: it blocks on the same
  // CountDownLatch the mock async context's onComplete/onError/onTimeout
  // listeners release, so it returns as soon as the SseEmitter's
  // complete() call (issued by Core.streamWorkerAnswer, on the worker
  // thread) finishes the request -- no manual Thread.sleep or busy-poll
  // needed.
  @Test
  void userPromptStreamHappyPathStreamsTokensThenDoneAndPersistsBothMessages()
      throws Exception {
    valkeyCache.delete("userPrompt:river");
    when(orchestratorService.route(anyList(), anyString()))
        .thenReturn(OrchestratorResponse.builder().requiredContexts(List.of())
            .reason("no context needed").build());
    doAnswer(invocation -> {
      Consumer<String> onToken = invocation.getArgument(3);
      onToken.accept("Hello");
      onToken.accept(", world!");
      return "Hello, world!";
    }).when(workerService).respondStream(any(), anyList(), anyString(), any());

    String signUpBody = mockMvc
        .perform(
            post("/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"river","password":"Str0ng!Pass"}"""))
        .andReturn().getResponse().getHeader("X-Auth-Token");
    String createResponse = mockMvc
        .perform(post("/chats").header("X-Auth-Token", signUpBody)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"AI Chat"}"""))
        .andExpect(status().isCreated()).andReturn().getResponse()
        .getContentAsString();
    String chatId = new ObjectMapper().readTree(createResponse).get("chats")
        .get(0).get("chatId").asText();

    MvcResult result = mockMvc
        .perform(post("/chats/" + chatId + "/messages/stream")
            .header("X-Auth-Token", signUpBody)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"message":"Hi there"}"""))
        .andExpect(request().asyncStarted()).andReturn();

    // Blocks until Core.streamWorkerAnswer's virtual thread calls
    // emitter.complete(), rather than racing it.
    result.getAsyncResult(5_000);

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(
        content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

    // Spring's SseEmitter serializes each event as "event:<name>\ndata:
    // <json>\n\n" -- confirmed here directly against the observed body
    // rather than assumed.
    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("event:token\ndata:");
    assertThat(body).contains("event:done\ndata:");

    mockMvc.perform(get("/chats/" + chatId).header("X-Auth-Token", signUpBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chat.messages", hasSize(2)))
        .andExpect(jsonPath("$.chat.messages[0].message").value("Hi there"))
        .andExpect(
            jsonPath("$.chat.messages[1].message").value("Hello, world!"));
  }

  @Test
  void createChatExceedingRateLimitReturns429WithRetryAfterHeader()
      throws Exception {
    valkeyCache.delete("createChat");
    valkeyCache.delete("createChat:ip:127.0.0.1");

    for (int i = 0; i < 30; i++) {
      mockMvc.perform(
          post("/chats").contentType(MediaType.APPLICATION_JSON).content("""
              {"chatTitle":"Chat %d"}""".formatted(i)));
    }

    mockMvc
        .perform(
            post("/chats").contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"One too many"}"""))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"));
  }

  @Test
  void requestExceedingMaxBodySizeReturns413() throws Exception {
    String oversizedTitle = "x".repeat(300_000);

    mockMvc
        .perform(
            post("/chats").contentType(MediaType.APPLICATION_JSON).content("""
                {"chatTitle":"%s"}""".formatted(oversizedTitle)))
        .andExpect(status().isPayloadTooLarge());
  }

  @Test
  void healthReturnsOk() throws Exception {
    mockMvc.perform(get("/health")).andExpect(status().isOk());
  }

  @Test
  void professionalDetailsReturnsOk() throws Exception {
    valkeyCache.delete("professionalDetails");

    mockMvc.perform(get("/details/professional")).andExpect(status().isOk())
        .andExpect(jsonPath("$.professionalDetails.resumeLink").exists());
  }

  @Test
  void leetcodeDetailsReturnsOk() throws Exception {
    valkeyCache.delete("leetcodeDetails");

    mockMvc.perform(get("/details/leetcode")).andExpect(status().isOk())
        .andExpect(jsonPath("$.leetcodeDetails[0].username").exists());
  }

  @Test
  void codeforcesDetailsReturnsOk() throws Exception {
    valkeyCache.delete("codeforcesDetails");

    mockMvc.perform(get("/details/codeforces")).andExpect(status().isOk())
        .andExpect(jsonPath("$.codeforcesDetails[0].handle").exists());
  }

  @Test
  void githubDetailsReturnsOk() throws Exception {
    valkeyCache.delete("githubDetails");

    mockMvc.perform(get("/details/github")).andExpect(status().isOk())
        .andExpect(jsonPath("$.githubDetails[0].username").exists());
  }

  @Test
  void personalityDetailsReturnsOk() throws Exception {
    valkeyCache.delete("personalityDetails");

    mockMvc.perform(get("/details/personality")).andExpect(status().isOk())
        .andExpect(jsonPath("$.personalityDetails.aboutMe").exists());
  }

  @Test
  void profileDetailsReturnsOk() throws Exception {
    valkeyCache.delete("profileDetails");

    mockMvc.perform(get("/details/profile")).andExpect(status().isOk())
        .andExpect(jsonPath("$.profileDetails.leetcodeUsernames").exists());
  }

  // Indexing into OpenSearch happens asynchronously via Kafka after chat
  // creation returns, so a search immediately afterward can miss it -- poll
  // until a match shows up or the deadline passes. /chats/search is
  // rate-limited (30 calls/60s, shared globally across every caller and
  // every test), so this polls slowly rather than busy-looping -- a tight
  // retry loop would exhaust that budget within a single test and start
  // failing unrelated later tests with 429s.
  private JsonNode awaitSearchResponse(final String authToken,
      final String query) throws Exception {
    long deadline = System.currentTimeMillis() + 20_000;
    while (System.currentTimeMillis() < deadline) {
      String body = mockMvc
          .perform(post("/chats/search").header("X-Auth-Token", authToken)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"query\":\"" + query + "\"}"))
          .andExpect(status().isOk()).andReturn().getResponse()
          .getContentAsString();
      JsonNode root = new ObjectMapper().readTree(body);
      if (!root.get("chats").isEmpty()) {
        return root;
      }
      Thread.sleep(2_000);
    }
    throw new AssertionError(
        "No search results for query \"" + query + "\" within timeout");
  }
}
