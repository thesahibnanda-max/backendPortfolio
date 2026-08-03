package net.sahibnanda.portfolio.core;

import static org.assertj.core.api.Assertions.assertThat;

import net.sahibnanda.portfolio.cache.ValkeyCache;
import net.sahibnanda.portfolio.config.AuthProperties;
import net.sahibnanda.portfolio.pojo.ChatRequestPOJO;
import net.sahibnanda.portfolio.pojo.ChatResponsePOJO;
import net.sahibnanda.portfolio.pojo.ErrorResponsePOJO;
import net.sahibnanda.portfolio.pojo.ListOfChatResponsePOJO;
import net.sahibnanda.portfolio.pojo.ResponsePOJO;
import net.sahibnanda.portfolio.pojo.UserGateRequestPOJO;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import net.sahibnanda.portfolio.utils.StringUtils;
import net.sahibnanda.portfolio.utils.TokenUtils;
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
  void createChatWithoutAuthTokenReturnsUnauthorizedError() {
    ResponsePOJO response =
        core.createChat(ChatRequestPOJO.builder().chatTitle("My Chat").build());

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void healthReturnsOkWhenDatabaseIsReachable() {
    ResponsePOJO response = core.health();

    assertThat(response.getHttpStatusCode()).isEqualTo(HttpStatus.OK);
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

  private ResponsePOJO createChat(final String token, final String title) {
    return core.createChat(ChatRequestPOJO.builder()
        .headers(Map.of(AUTH_HEADER, token)).chatTitle(title).build());
  }

  private String signUpAndGetToken(final String username) {
    ResponsePOJO response = core.signUp(UserGateRequestPOJO.builder()
        .username(username).password("Str0ng!Pass").build());
    return response.getHeaders().get(AUTH_HEADER);
  }
}
