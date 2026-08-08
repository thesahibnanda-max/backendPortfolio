package net.sahibnanda.portfolio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class ControllerTest extends AbstractRepositoryIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ValkeyCache valkeyCache;

  // createChat's global rate-limit key is deliberately saturated by
  // createChatExceedingRateLimitReturns429WithRetryAfterHeader below --
  // reset it before every test so that doesn't leak a spent budget into
  // whichever test happens to run next in the same 60s window.
  @BeforeEach
  void resetCreateChatRateLimit() {
    valkeyCache.delete("createChat");
    valkeyCache.delete("createChat:ip:127.0.0.1");
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
