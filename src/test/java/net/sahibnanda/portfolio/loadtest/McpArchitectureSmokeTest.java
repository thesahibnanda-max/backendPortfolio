package net.sahibnanda.portfolio.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.sahibnanda.portfolio.middleware.SessionHeaderResolver;
import net.sahibnanda.portfolio.utils.JsonUtils;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual-only smoke check for the {@code "architecture": "mcp"} chat pipeline,
 * run against a LIVE backend instance -- e.g. one started via
 * {@code docker run --env-file stage.env backend-portfolio} per README.md's
 * "Testing the MCP architecture manually" section, with a real {@code
 * GROQ_API_KEYS} in {@code stage.env}. Not a load test (unlike
 * {@link ChatStreamLoadTest}, which this class's HTTP client style otherwise
 * mirrors) -- it sends exactly one blocking and one streaming request and
 * checks each answers using the MCP pipeline.
 *
 * <p>
 * To run: build and start the image (see README.md's Docker section), then:
 *
 * <pre>{@code
 * export MCP_TEST_BASE_URL=http://localhost:8080   # optional, this is the default
 * mvn -Dtest=McpArchitectureSmokeTest test
 * }</pre>
 *
 * then temporarily remove the {@code @Disabled} annotation.
 */
@Disabled("Manual-only: run against a live backend instance, see class Javadoc.")
class McpArchitectureSmokeTest {

  /** Default backend base URL, used when {@code MCP_TEST_BASE_URL} is unset. */
  private static final String DEFAULT_BASE_URL = "http://localhost:8080";

  /** Environment variable overriding the backend base URL. */
  private static final String BASE_URL_ENV_VAR = "MCP_TEST_BASE_URL";

  /** Title used for every chat this smoke test creates. */
  private static final String CHAT_TITLE = "MCP smoke test chat";

  /** Message text sent, chosen to require a real tool call to answer well. */
  private static final String MESSAGE_TEXT = "What is your LeetCode rating?";

  /** Media type used for every JSON request body this test sends. */
  private static final MediaType JSON = MediaType.get("application/json");

  /** Shared OkHttp client used for every request this test issues. */
  private static final OkHttpClient HTTP_CLIENT =
      new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(10))
          .readTimeout(Duration.ofSeconds(60))
          .writeTimeout(Duration.ofSeconds(60)).build();

  @Test
  void blockingMcpRequestAnswersUsingTheMcpArchitecture() throws IOException {
    String baseUrl = baseUrl();
    Chat chat = createAnonymousChat(baseUrl);

    Request request =
        new Request.Builder()
            .url(baseUrl + "/chats/" + chat.chatId() + "/messages")
            .header(SessionHeaderResolver.X_SESSION_ID, chat.sessionId())
            .post(RequestBody.create(
                JsonUtils.toJson(
                    Map.of("message", MESSAGE_TEXT, "architecture", "mcp")),
                JSON))
            .build();

    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
      ResponseBody body = response.body();
      String bodyString = body != null ? body.string() : "";
      System.out.println(bodyString);
      assertThat(bodyString).contains("\"architecture\":\"mcp\"");
    }
  }

  @Test
  void streamingMcpRequestCompletesWithADoneEvent() throws IOException {
    String baseUrl = baseUrl();
    Chat chat = createAnonymousChat(baseUrl);

    Request request =
        new Request.Builder()
            .url(baseUrl + "/chats/" + chat.chatId() + "/messages/stream")
            .header(SessionHeaderResolver.X_SESSION_ID, chat.sessionId())
            .post(RequestBody.create(
                JsonUtils.toJson(
                    Map.of("message", MESSAGE_TEXT, "architecture", "mcp")),
                JSON))
            .build();

    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
      assertThat(response.isSuccessful()).isTrue();
      ResponseBody body = response.body();
      BufferedSource source = body != null ? body.source() : null;
      boolean sawDone = false;
      if (source != null) {
        String line;
        while ((line = source.readUtf8Line()) != null) {
          System.out.println(line);
          if ("event:done".equals(line)) {
            sawDone = true;
            break;
          }
        }
      }
      assertThat(sawDone).isTrue();
    }
  }

  private static String baseUrl() {
    return System.getenv().getOrDefault(BASE_URL_ENV_VAR, DEFAULT_BASE_URL);
  }

  private static Chat createAnonymousChat(final String baseUrl)
      throws IOException {
    Request request = new Request.Builder().url(baseUrl + "/chats")
        .post(RequestBody
            .create(JsonUtils.toJson(Map.of("chatTitle", CHAT_TITLE)), JSON))
        .build();

    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
      String sessionId = response.header(SessionHeaderResolver.X_SESSION_ID);
      ResponseBody body = response.body();
      String bodyString = body != null ? body.string() : "";
      ChatListResponse parsed =
          JsonUtils.fromJson(bodyString, ChatListResponse.class);
      return new Chat(sessionId, parsed.chats().get(0).chatId());
    }
  }

  /**
   * The session id and chat id returned by a successful anonymous chat creation
   * call.
   *
   * @param sessionId the {@value SessionHeaderResolver#X_SESSION_ID} value the
   *        backend assigned this caller
   * @param chatId the identifier of the newly created chat
   */
  private record Chat(String sessionId, String chatId) {
  }

  /**
   * Minimal shape of the {@code POST /chats} JSON response this test needs.
   *
   * @param chats every chat in the response, in the same order the backend
   *        returned them
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ChatListResponse(List<ChatIdOnly> chats) {
  }

  /**
   * Minimal shape of a single chat within the {@code POST /chats} JSON
   * response: just its id.
   *
   * @param chatId the chat's identifier
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ChatIdOnly(String chatId) {
  }
}
