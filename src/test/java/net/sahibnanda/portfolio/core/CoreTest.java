package net.sahibnanda.portfolio.core;

import static org.assertj.core.api.Assertions.assertThat;

import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.config.AuthProperties;
import net.sahibnanda.portfolio.config.ChatLimitsProperties;
import net.sahibnanda.portfolio.pojo.ChatRequestPOJO;
import net.sahibnanda.portfolio.pojo.ChatResponsePOJO;
import net.sahibnanda.portfolio.pojo.CodeforcesDetailsResponsePOJO;
import net.sahibnanda.portfolio.pojo.ErrorResponsePOJO;
import net.sahibnanda.portfolio.pojo.GitHubDetailsResponsePOJO;
import net.sahibnanda.portfolio.pojo.LeetcodeDetailsResponsePOJO;
import net.sahibnanda.portfolio.pojo.ListOfChatResponsePOJO;
import net.sahibnanda.portfolio.pojo.PersonalityDetailsResponsePOJO;
import net.sahibnanda.portfolio.pojo.ProfessionalDetailsResponsePOJO;
import net.sahibnanda.portfolio.pojo.ProfileDetailsResponsePOJO;
import net.sahibnanda.portfolio.pojo.ResponsePOJO;
import net.sahibnanda.portfolio.pojo.SearchRequestPOJO;
import net.sahibnanda.portfolio.pojo.SearchResponsePOJO;
import net.sahibnanda.portfolio.pojo.UserGateRequestPOJO;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.services.UserChatService;
import net.sahibnanda.portfolio.utils.StringUtils;
import net.sahibnanda.portfolio.utils.TokenUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.Map;

class CoreTest extends AbstractRepositoryIntegrationTest {

  private static final String AUTH_HEADER = "X-Auth-Token";

  @Autowired
  private Core core;

  @Autowired
  private AuthProperties authProperties;

  @Autowired
  private ValkeyCache valkeyCache;

  // createChat's global rate-limit key is now deliberately saturated by a
  // few tests below (the IP-tier regression tests) -- reset it before every
  // test so those don't leak a spent budget into whichever test happens to
  // run next in the same 60s window. signUp's global key needs the same
  // treatment: this class signs up 20+ distinct users across its tests, all
  // well within signUp's 30-req/60s window on fast hardware, so without a
  // reset the later signUp/login calls intermittently see a real 429 from
  // budget the earlier tests already spent, not the behavior under test.
  @BeforeEach
  void resetSharedRateLimits() {
    valkeyCache.delete("createChat");
    valkeyCache.delete("signUp");
  }

  @Autowired
  private ChatLimitsProperties chatLimits;

  @Autowired
  private UserChatService userChatService;

  @Test
  void signUpCreatesUserAndReturnsDecryptableToken() {
    ResponsePOJO response = core.signUp(UserGateRequestPOJO.builder()
        .username("alice").password("Str0ng!Pass").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(TokenUtils.decrypt(response.getHeaders().get(AUTH_HEADER),
        authProperties.secretKey())).isEqualTo("alice");
  }

  @Test
  void loginWithCorrectPasswordSucceeds() {
    core.signUp(UserGateRequestPOJO.builder().username("bob")
        .password("Str0ng!Pass").build());

    ResponsePOJO response = core.login(UserGateRequestPOJO.builder()
        .username("bob").password("Str0ng!Pass").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(TokenUtils.decrypt(response.getHeaders().get(AUTH_HEADER),
        authProperties.secretKey())).isEqualTo("bob");
  }

  @Test
  void loginWithWrongPasswordReturnsUnauthorizedError() {
    core.signUp(UserGateRequestPOJO.builder().username("carol")
        .password("Str0ng!Pass").build());

    ResponsePOJO response = core.login(UserGateRequestPOJO.builder()
        .username("carol").password("Wrong!Pass1").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response).isInstanceOf(ErrorResponsePOJO.class);
    ErrorResponsePOJO error = (ErrorResponsePOJO) response;
    assertThat(error.getShowMessageAsIs()).isTrue();
    assertThat(error.getErrorMessage()).contains("carol");
  }

  @Test
  void loginWithUnknownUsernameReturnsNotFoundError() {
    ResponsePOJO response = core.login(UserGateRequestPOJO.builder()
        .username("nobody").password("Str0ng!Pass").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(((ErrorResponsePOJO) response).getShowMessageAsIs()).isTrue();
  }

  @Test
  void signUpWithWeakPasswordReturnsBadRequestAndDoesNotCreateUser() {
    ResponsePOJO signUpResponse = core.signUp(UserGateRequestPOJO.builder()
        .username("dave").password("short").build());

    assertThat(signUpResponse.getHttpStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    ResponsePOJO loginResponse = core.login(UserGateRequestPOJO.builder()
        .username("dave").password("Str0ng!Pass").build());

    assertThat(loginResponse.getHttpStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void signUpWithDuplicateUsernameReturnsConflictError() {
    core.signUp(UserGateRequestPOJO.builder().username("erin")
        .password("Str0ng!Pass").build());

    ResponsePOJO response = core.signUp(UserGateRequestPOJO.builder()
        .username("erin").password("Str0ng!Pass").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void createChatReturnsCreatedWithTheNewChatInTheList() {
    String token = signUpAndGetToken("frank");

    ResponsePOJO response = createChat(token, "My Chat");

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.CREATED);
    ListOfChatResponsePOJO list = (ListOfChatResponsePOJO) response;
    assertThat(list.getChats()).hasSize(1);
    assertThat(list.getChats().get(0).getChatTitle()).isEqualTo("My Chat");
  }

  @Test
  void createChatWithBlankTitleReturnsBadRequestError() {
    String token = signUpAndGetToken("piper");

    ResponsePOJO response = core.createChat(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token)).build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response).isInstanceOf(ErrorResponsePOJO.class);
    assertThat(((ErrorResponsePOJO) response).getShowMessageAsIs()).isTrue();
  }

  @Test
  void allChatsReturnsEveryChatForTheUser() {
    String token = signUpAndGetToken("grace");
    createChat(token, "Chat 1");
    createChat(token, "Chat 2");

    ResponsePOJO response = core.allChats(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token)).build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(((ListOfChatResponsePOJO) response).getChats()).hasSize(2);
  }

  @Test
  void getChatByIdReturnsTheRequestedChat() {
    String token = signUpAndGetToken("henry");
    ListOfChatResponsePOJO created =
        (ListOfChatResponsePOJO) createChat(token, "My Chat");
    String chatId = created.getChats().get(0).getChatId();

    ResponsePOJO response = core.getChatById(ChatRequestPOJO.builder()
        .headers(Map.of(AUTH_HEADER, token)).chatId(chatId).build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(((ChatResponsePOJO) response).getChat().getChatTitle())
        .isEqualTo("My Chat");
  }

  @Test
  void getChatByIdForUnknownChatReturnsNotFoundError() {
    String token = signUpAndGetToken("iris");

    ResponsePOJO response = core.getChatById(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
            .chatId(StringUtils.generateUlid()).build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void updateTitleReturnsTheRenamedChat() {
    String token = signUpAndGetToken("jack");
    ListOfChatResponsePOJO created =
        (ListOfChatResponsePOJO) createChat(token, "Original");
    String chatId = created.getChats().get(0).getChatId();

    ResponsePOJO response = core.updateTitle(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
            .chatId(chatId).chatTitle("Renamed").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(((ChatResponsePOJO) response).getChat().getChatTitle())
        .isEqualTo("Renamed");
  }

  @Test
  void searchChatReturnsMatchingChatsSortedByScore() {
    // searchChat's rate-limit keys, like userPrompt's, live in the shared
    // Valkey instance and aren't reset between test runs -- clear them so
    // this test's budget always starts fresh.
    valkeyCache.delete("searchChat");
    valkeyCache.delete("searchChat:mia");
    String token = signUpAndGetToken("mia");
    createChat(token, "Kyoto travel itinerary");

    SearchResponsePOJO response =
        awaitSearchResults(token, "kyoto travel itinerary");

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getChats()).isNotEmpty();
    String chatId = response.getChats().get(0).getChatId();
    assertThat(response.getScores()).containsKey(chatId);
  }

  @Test
  void searchChatWithBlankQueryReturnsBadRequestError() {
    valkeyCache.delete("searchChat");
    valkeyCache.delete("searchChat:noah");
    String token = signUpAndGetToken("noah");

    ResponsePOJO response = core.searchChat(SearchRequestPOJO.builder()
        .headers(Map.of(AUTH_HEADER, token)).query(" ").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(((ErrorResponsePOJO) response).getShowMessageAsIs()).isTrue();
  }

  @Test
  void searchChatWithoutAuthTokenReturnsUnauthorizedError() {
    ResponsePOJO response =
        core.searchChat(SearchRequestPOJO.builder().query("anything").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void createChatWithoutAuthTokenCreatesAnonymousChat() {
    // Real requests always go through Controller, which resolves and sets
    // sessionId before calling Core -- simulate that here since this test
    // calls Core directly.
    ResponsePOJO response = core.createChat(ChatRequestPOJO.builder()
        .chatTitle("My Chat").sessionId(StringUtils.generateUlid()).build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.CREATED);
    ListOfChatResponsePOJO list = (ListOfChatResponsePOJO) response;
    assertThat(list.getChats()).hasSize(1);
    assertThat(list.getChats().get(0).getChatTitle()).isEqualTo("My Chat");
    assertThat(list.getChats().get(0).getUsername()).isNull();
  }

  @Test
  void createChatWithoutAuthTokenOrSessionIdReturnsInternalServerError() {
    // Guards the SessionResolutionException fallback for a request that
    // somehow reached Core with neither an auth token nor a resolved
    // session id -- should never happen once Controller always resolves
    // one, but Core must still fail safely rather than crash unhandled.
    ResponsePOJO response =
        core.createChat(ChatRequestPOJO.builder().chatTitle("My Chat").build());

    assertThat(response.getHttpStatusCode())
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Test
  void healthReturnsOkWhenDatabaseIsReachable() {
    ResponsePOJO response = core.health();

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void professionalDetailsReturnsOk() {
    valkeyCache.delete("professionalDetails");

    ResponsePOJO response = core.professionalDetails();

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    ProfessionalDetailsResponsePOJO details =
        (ProfessionalDetailsResponsePOJO) response;
    assertThat(details.getProfessionalDetails()).isNotNull();
    assertThat(details.getProfessionalDetails().getResumeLink()).isNotBlank();
  }

  @Test
  void professionalDetailsExceedingRateLimitReturnsTooManyRequests() {
    valkeyCache.delete("professionalDetails");

    ResponsePOJO response = null;
    for (int i = 0; i < 31; i++) {
      response = core.professionalDetails();
    }

    assertThat(response.getHttpStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void leetcodeDetailsReturnsOk() {
    valkeyCache.delete("leetcodeDetails");

    ResponsePOJO response = core.leetcodeDetails();

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    LeetcodeDetailsResponsePOJO details =
        (LeetcodeDetailsResponsePOJO) response;
    assertThat(details.getLeetcodeDetails()).isNotNull().isNotEmpty();
    assertThat(details.getLeetcodeDetails().getFirst().getUsername())
        .isEqualTo("imsahibnanda");
  }

  @Test
  void leetcodeDetailsExceedingRateLimitReturnsTooManyRequests() {
    valkeyCache.delete("leetcodeDetails");

    ResponsePOJO response = null;
    for (int i = 0; i < 31; i++) {
      response = core.leetcodeDetails();
    }

    assertThat(response.getHttpStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void codeforcesDetailsReturnsOk() {
    valkeyCache.delete("codeforcesDetails");

    ResponsePOJO response = core.codeforcesDetails();

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    CodeforcesDetailsResponsePOJO details =
        (CodeforcesDetailsResponsePOJO) response;
    assertThat(details.getCodeforcesDetails()).isNotNull().isNotEmpty();
    assertThat(details.getCodeforcesDetails().getFirst().getHandle())
        .isEqualTo("shisukenohara");
  }

  @Test
  void codeforcesDetailsExceedingRateLimitReturnsTooManyRequests() {
    valkeyCache.delete("codeforcesDetails");

    ResponsePOJO response = null;
    for (int i = 0; i < 31; i++) {
      response = core.codeforcesDetails();
    }

    assertThat(response.getHttpStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void githubDetailsReturnsOk() {
    valkeyCache.delete("githubDetails");

    ResponsePOJO response = core.githubDetails();

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    GitHubDetailsResponsePOJO details = (GitHubDetailsResponsePOJO) response;
    assertThat(details.getGithubDetails()).isNotNull().hasSize(2);
  }

  @Test
  void githubDetailsExceedingRateLimitReturnsTooManyRequests() {
    valkeyCache.delete("githubDetails");

    ResponsePOJO response = null;
    for (int i = 0; i < 31; i++) {
      response = core.githubDetails();
    }

    assertThat(response.getHttpStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void personalityDetailsReturnsOk() {
    valkeyCache.delete("personalityDetails");

    ResponsePOJO response = core.personalityDetails();

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    PersonalityDetailsResponsePOJO details =
        (PersonalityDetailsResponsePOJO) response;
    assertThat(details.getPersonalityDetails()).isNotNull();
    assertThat(details.getPersonalityDetails().getAboutMe()).isNotBlank();
  }

  @Test
  void personalityDetailsExceedingRateLimitReturnsTooManyRequests() {
    valkeyCache.delete("personalityDetails");

    ResponsePOJO response = null;
    for (int i = 0; i < 31; i++) {
      response = core.personalityDetails();
    }

    assertThat(response.getHttpStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void profileDetailsReturnsOk() {
    valkeyCache.delete("profileDetails");

    ResponsePOJO response = core.profileDetails();

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    ProfileDetailsResponsePOJO details = (ProfileDetailsResponsePOJO) response;
    assertThat(details.getProfileDetails()).isNotNull();
    assertThat(details.getProfileDetails().getLeetcodeUsernames())
        .containsExactly("imsahibnanda");
  }

  @Test
  void profileDetailsExceedingRateLimitReturnsTooManyRequests() {
    valkeyCache.delete("profileDetails");

    ResponsePOJO response = null;
    for (int i = 0; i < 31; i++) {
      response = core.profileDetails();
    }

    assertThat(response.getHttpStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  @Disabled("Manual-only: requires a real GROQ_API_KEYS in a local .env.")
  void userPromptRespondsAndPersistsBothMessages() {
    String token = signUpAndGetToken("kim");
    ListOfChatResponsePOJO created =
        (ListOfChatResponsePOJO) createChat(token, "AI Chat");
    String chatId = created.getChats().get(0).getChatId();

    ResponsePOJO response = core.userPrompt(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
            .chatId(chatId).message("Hello!").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(((ChatResponsePOJO) response).getChat().getMessages())
        .hasSize(2);
  }

  @Test
  void userPromptExceedingRateLimitReturnsTooManyRequests() {
    // These rate-limit keys live in the real, shared Valkey instance and
    // aren't reset between test runs (unlike Postgres, via
    // cleanDatabase()) -- clear them so this test's count always starts
    // fresh, regardless of leftover state from a previous run.
    valkeyCache.delete("userPrompt");
    valkeyCache.delete("userPrompt:liam");

    String token = signUpAndGetToken("liam");

    ResponsePOJO response = null;
    for (int i = 0; i < 6; i++) {
      response = core.userPrompt(
          ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
              .chatId(StringUtils.generateUlid()).message("Hi").build());
    }

    assertThat(response.getHttpStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void createChatWithTooLongTitleReturnsBadRequestError() {
    String token = signUpAndGetToken("oliver");
    String tooLongTitle = "x".repeat(chatLimits.maxChatTitleLength() + 1);

    ResponsePOJO response = core.createChat(ChatRequestPOJO.builder()
        .headers(Map.of(AUTH_HEADER, token)).chatTitle(tooLongTitle).build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(((ErrorResponsePOJO) response).getShowMessageAsIs()).isTrue();
  }

  @Test
  void updateTitleWithTooLongTitleReturnsBadRequestError() {
    String token = signUpAndGetToken("penny");
    ListOfChatResponsePOJO created =
        (ListOfChatResponsePOJO) createChat(token, "Original");
    String chatId = created.getChats().get(0).getChatId();
    String tooLongTitle = "x".repeat(chatLimits.maxChatTitleLength() + 1);

    ResponsePOJO response = core.updateTitle(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
            .chatId(chatId).chatTitle(tooLongTitle).build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void userPromptWithTooLongMessageReturnsBadRequestError() {
    valkeyCache.delete("userPrompt");
    valkeyCache.delete("userPrompt:quinn");
    String token = signUpAndGetToken("quinn");
    ListOfChatResponsePOJO created =
        (ListOfChatResponsePOJO) createChat(token, "AI Chat");
    String chatId = created.getChats().get(0).getChatId();
    String tooLongMessage = "x".repeat(chatLimits.maxMessageLength() + 1);

    ResponsePOJO response = core.userPrompt(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
            .chatId(chatId).message(tooLongMessage).build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void searchChatWithTooLongQueryReturnsBadRequestError() {
    valkeyCache.delete("searchChat");
    valkeyCache.delete("searchChat:ruby");
    String token = signUpAndGetToken("ruby");
    String tooLongQuery = "x".repeat(chatLimits.maxSearchQueryLength() + 1);

    ResponsePOJO response = core.searchChat(SearchRequestPOJO.builder()
        .headers(Map.of(AUTH_HEADER, token)).query(tooLongQuery).build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void userPromptExceedingPerChatMessageCapReturnsBadRequestError() {
    valkeyCache.delete("userPrompt");
    valkeyCache.delete("userPrompt:sara");
    String token = signUpAndGetToken("sara");
    ListOfChatResponsePOJO created =
        (ListOfChatResponsePOJO) createChat(token, "Long Chat");
    String chatId = created.getChats().get(0).getChatId();
    for (int i = 0; i < chatLimits.maxMessagesPerChat(); i++) {
      userChatService.saveUserMessage("sara", chatId, "msg " + i);
    }

    ResponsePOJO response = core.userPrompt(
        ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
            .chatId(chatId).message("one more").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void createChatRotatingSessionIdFromTheSameIpStillHitsARateLimit() {
    valkeyCache.delete("createChat");
    String sameIp = "203.0.113.5";
    valkeyCache.delete("createChat:ip:" + sameIp);

    // A rotated X-Session-Id per request defeats the per-session tier (each
    // key is fresh), but the request must still be blocked once some tier's
    // budget is exhausted -- proves rotation alone isn't a free pass.
    ResponsePOJO response = null;
    for (int i = 0; i < 31; i++) {
      response =
          core.createChat(ChatRequestPOJO.builder().chatTitle("Chat " + i)
              .sessionId(StringUtils.generateUlid()).ipAddress(sameIp).build());
    }

    assertThat(response.getHttpStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void createChatIsBlockedByTheIpTierAloneEvenWithFreshGlobalAndUserBudget() {
    String ip = "198.51.100.9";
    valkeyCache.delete("createChat");
    valkeyCache.delete("createChat:ip:" + ip);
    // Saturate only the IP-tier key directly, leaving the global and
    // per-session tiers completely fresh -- isolates that the IP tier is
    // independently enforced, not just riding along with the global cap.
    for (int i = 0; i < 30; i++) {
      valkeyCache.incrementWithExpire("createChat:ip:" + ip, 60);
    }

    ResponsePOJO response =
        core.createChat(ChatRequestPOJO.builder().chatTitle("Chat")
            .sessionId(StringUtils.generateUlid()).ipAddress(ip).build());

    assertThat(response.getHttpStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void userPromptExceedingRateLimitReturnsRetryAfterHeader() {
    valkeyCache.delete("userPrompt");
    valkeyCache.delete("userPrompt:oscar");
    String token = signUpAndGetToken("oscar");

    ResponsePOJO response = null;
    for (int i = 0; i < 6; i++) {
      response = core.userPrompt(
          ChatRequestPOJO.builder().headers(Map.of(AUTH_HEADER, token))
              .chatId(StringUtils.generateUlid()).message("Hi").build());
    }

    assertThat(response.getHttpStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeaders()).containsEntry("Retry-After", "60");
  }

  private ResponsePOJO createChat(final String token, final String title) {
    return core.createChat(ChatRequestPOJO.builder()
        .headers(Map.of(AUTH_HEADER, token)).chatTitle(title).build());
  }

  // Indexing into OpenSearch happens asynchronously via Kafka after
  // createChat returns, so a search immediately afterward can miss it --
  // poll until a match shows up or the deadline passes. searchChat is
  // rate-limited (30 calls/60s, shared globally across every caller of
  // this method and every test), so this polls slowly rather than
  // busy-looping -- a tight retry loop would exhaust that budget within
  // a single test and start failing unrelated later tests with 429s.
  private SearchResponsePOJO awaitSearchResults(final String token,
      final String query) {
    long deadline = System.currentTimeMillis() + 20_000;
    while (System.currentTimeMillis() < deadline) {
      ResponsePOJO response = core.searchChat(SearchRequestPOJO.builder()
          .headers(Map.of(AUTH_HEADER, token)).query(query).build());
      if (response instanceof SearchResponsePOJO searchResponse
          && !searchResponse.getChats().isEmpty()) {
        return searchResponse;
      }
      sleepQuietly();
    }
    throw new AssertionError(
        "No search results for query \"" + query + "\" within timeout");
  }

  private void sleepQuietly() {
    try {
      Thread.sleep(2_000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while polling", e);
    }
  }

  private String signUpAndGetToken(final String username) {
    ResponsePOJO response = core.signUp(UserGateRequestPOJO.builder()
        .username(username).password("Str0ng!Pass").build());
    return response.getHeaders().get(AUTH_HEADER);
  }
}
