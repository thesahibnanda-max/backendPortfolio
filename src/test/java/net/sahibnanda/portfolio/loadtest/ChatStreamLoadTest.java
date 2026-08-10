package net.sahibnanda.portfolio.loadtest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
 * Manual-only load test for the SSE streaming chat endpoint: spins up N
 * concurrent "users," each creating an anonymous chat and streaming one
 * message, measuring time-to-first-byte and total stream duration.
 *
 * <p>
 * Run against a LIVE backend instance with real Groq credentials configured --
 * each concurrent user triggers a real LLM call, so keep concurrency modest
 * unless you intend to spend real API budget. To run:
 * 
 * <pre>{@code
 * export LOAD_TEST_BASE_URL=http://localhost:8080   # optional, this is the default
 * export LOAD_TEST_CONCURRENCY=10                    # optional, this is the default
 * mvn -Dtest=ChatStreamLoadTest test
 * }</pre>
 * 
 * then temporarily remove the {@code @Disabled} annotation.
 *
 * <p>
 * <b>Why this has no assertions:</b> the single {@code @Test} method below
 * never asserts on a latency budget. That is intentional, not an oversight.
 * This class is a load-generation/reporting tool structured as a JUnit test
 * purely so it can reuse this project's existing Maven/dependency
 * infrastructure (JUnit runner, OkHttp, Jackson via {@link JsonUtils}) -- it is
 * explicitly NOT a correctness test. There is no established latency baseline
 * to assert against yet, and this environment's infrastructure
 * (Docker/Testcontainers) has already shown real timing variability run to run,
 * so a hard-coded threshold here would be either meaninglessly loose or a
 * source of flaky failures unrelated to real regressions. Instead, this test
 * prints percentiles and a success/failure breakdown for a human to read,
 * record, and compare across runs by hand.
 */
@Disabled("Manual-only: run against a live backend instance, see class Javadoc.")
class ChatStreamLoadTest {

  /**
   * Default backend base URL, used when {@code LOAD_TEST_BASE_URL} is unset.
   */
  private static final String DEFAULT_BASE_URL = "http://localhost:8080";

  /**
   * Default number of concurrent simulated users, used when
   * {@code LOAD_TEST_CONCURRENCY} is unset. Kept low by default because each
   * concurrent user triggers a real, billable Groq LLM call against a live
   * backend.
   */
  private static final int DEFAULT_CONCURRENCY = 10;

  /** Environment variable overriding the backend base URL. */
  private static final String BASE_URL_ENV_VAR = "LOAD_TEST_BASE_URL";

  /** Environment variable overriding the concurrent user count. */
  private static final String CONCURRENCY_ENV_VAR = "LOAD_TEST_CONCURRENCY";

  /** Title used for every chat this load test creates. */
  private static final String CHAT_TITLE = "Load test chat";

  /** Message text streamed by every simulated user. */
  private static final String MESSAGE_TEXT = "Say hello in one short sentence.";

  /** Media type used for every JSON request body this test sends. */
  private static final MediaType JSON = MediaType.get("application/json");

  /** Connect timeout, in seconds, for the shared HTTP client. */
  private static final int CONNECT_TIMEOUT_SECONDS = 10;

  /**
   * Read timeout, in seconds, for the shared HTTP client -- generous, since a
   * full SSE stream can legitimately take a while to complete.
   */
  private static final int READ_TIMEOUT_SECONDS = 120;

  /** Write timeout, in seconds, for the shared HTTP client. */
  private static final int WRITE_TIMEOUT_SECONDS = 60;

  /**
   * How long to wait for every simulated user to finish before failing the
   * whole run rather than hanging indefinitely.
   */
  private static final long OVERALL_TIMEOUT_SECONDS = 120L;

  /** SSE line marking successful completion of a stream. */
  private static final String EVENT_DONE_LINE = "event:done";

  /** SSE line marking a failed stream. */
  private static final String EVENT_ERROR_LINE = "event:error";

  /** Percentile computed and printed for both TTFB and total duration. */
  private static final double PERCENTILE_50 = 0.50;

  /** Percentile computed and printed for both TTFB and total duration. */
  private static final double PERCENTILE_95 = 0.95;

  /** Percentile computed and printed for both TTFB and total duration. */
  private static final double PERCENTILE_99 = 0.99;

  /** Converts a duration in nanoseconds to milliseconds for display. */
  private static final double NANOS_PER_MILLI = 1_000_000.0;

  /**
   * Shared OkHttp client used for every request this test issues, with fixed
   * connect/read/write timeouts.
   */
  private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
      .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
      .readTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
      .writeTimeout(Duration.ofSeconds(WRITE_TIMEOUT_SECONDS)).build();

  /**
   * Spins up {@code LOAD_TEST_CONCURRENCY} (default
   * {@value #DEFAULT_CONCURRENCY}) concurrent virtual-thread "users" against
   * {@code
   * LOAD_TEST_BASE_URL} (default {@value #DEFAULT_BASE_URL}), each creating an
   * anonymous chat and streaming one message, then prints latency percentiles
   * and a success/failure breakdown. Never asserts on latency -- see the class
   * Javadoc for why.
   *
   * @throws InterruptedException if interrupted while waiting for every
   *         simulated user to finish
   */
  @Test
  void streamManyConcurrentChatsAndReportLatencyPercentiles()
      throws InterruptedException {
    String baseUrl =
        System.getenv().getOrDefault(BASE_URL_ENV_VAR, DEFAULT_BASE_URL);
    int concurrency =
        Integer.parseInt(System.getenv().getOrDefault(CONCURRENCY_ENV_VAR,
            String.valueOf(DEFAULT_CONCURRENCY)));

    ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
    CountDownLatch latch = new CountDownLatch(concurrency);

    for (int i = 0; i < concurrency; i++) {
      Thread.ofVirtual().start(() -> {
        try {
          results.add(runSingleUser(baseUrl));
        } finally {
          latch.countDown();
        }
      });
    }

    boolean completedInTime =
        latch.await(OVERALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!completedInTime) {
      throw new AssertionError("Load test did not complete within "
          + OVERALL_TIMEOUT_SECONDS + " seconds for concurrency=" + concurrency
          + "; the backend may be stuck or a stream never closed.");
    }

    printSummary(results, concurrency);
  }

  /**
   * Simulates a single user: creates an anonymous chat, then streams one
   * message and measures time-to-first-byte and total stream duration. Never
   * throws -- every failure mode is captured into the returned {@link Result}
   * instead, so one user's failure never aborts the others or hangs the overall
   * test.
   *
   * @param baseUrl the backend base URL to call
   * @return this user's outcome and timings
   */
  private static Result runSingleUser(final String baseUrl) {
    ChatCreation creation;
    try {
      creation = createAnonymousChat(baseUrl);
    } catch (Exception e) {
      return Result.failedBeforeStreaming(describeError(e));
    }

    long startTime = System.nanoTime();
    long timeToFirstByteNanos = -1L;
    boolean success = false;
    String errorDescription = null;

    Request request = new Request.Builder()
        .url(baseUrl + "/chats/" + creation.chatId() + "/messages/stream")
        .header(SessionHeaderResolver.X_SESSION_ID, creation.sessionId())
        .post(RequestBody
            .create(JsonUtils.toJson(Map.of("message", MESSAGE_TEXT)), JSON))
        .build();

    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        long totalDurationNanos = System.nanoTime() - startTime;
        return new Result(false, timeToFirstByteNanos, totalDurationNanos,
            "HTTP " + response.code() + " from messages/stream");
      }

      ResponseBody body = response.body();
      BufferedSource source = body != null ? body.source() : null;
      if (source != null) {
        String line;
        while ((line = source.readUtf8Line()) != null) {
          if (line.isBlank()) {
            continue;
          }
          if (timeToFirstByteNanos < 0) {
            timeToFirstByteNanos = System.nanoTime() - startTime;
          }
          if (EVENT_DONE_LINE.equals(line)) {
            success = true;
            break;
          }
          if (EVENT_ERROR_LINE.equals(line)) {
            errorDescription = "received " + EVENT_ERROR_LINE + " frame";
            break;
          }
        }
      }
    } catch (IOException e) {
      long totalDurationNanos = System.nanoTime() - startTime;
      return new Result(false, timeToFirstByteNanos, totalDurationNanos,
          describeError(e));
    }

    long totalDurationNanos = System.nanoTime() - startTime;
    if (!success && errorDescription == null) {
      errorDescription = "stream closed without " + EVENT_DONE_LINE + " or "
          + EVENT_ERROR_LINE;
    }
    return new Result(success, timeToFirstByteNanos, totalDurationNanos,
        errorDescription);
  }

  /**
   * Creates a brand-new anonymous chat and captures the session id the backend
   * assigns it.
   *
   * @param baseUrl the backend base URL to call
   * @return the assigned session id and the new chat's id
   * @throws IOException if the call fails, the response is not successful, the
   *         {@value SessionHeaderResolver#X_SESSION_ID} response header is
   *         absent, or the response body's {@code chats[0].chatId} is missing
   */
  private static ChatCreation createAnonymousChat(final String baseUrl)
      throws IOException {
    RequestBody requestBody = RequestBody
        .create(JsonUtils.toJson(Map.of("chatTitle", CHAT_TITLE)), JSON);
    Request request =
        new Request.Builder().url(baseUrl + "/chats").post(requestBody).build();

    try (Response response = HTTP_CLIENT.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("POST /chats failed. HTTP " + response.code());
      }

      String sessionId = response.header(SessionHeaderResolver.X_SESSION_ID);
      if (sessionId == null || sessionId.isBlank()) {
        throw new IOException("POST /chats response missing "
            + SessionHeaderResolver.X_SESSION_ID + " header.");
      }

      ResponseBody body = response.body();
      String responseBody = body != null ? body.string() : "";
      ChatListResponse parsed =
          JsonUtils.fromJson(responseBody, ChatListResponse.class);
      if (parsed.chats() == null || parsed.chats().isEmpty()) {
        throw new IOException(
            "POST /chats response missing chats[0].chatId. Body: "
                + responseBody);
      }

      String chatId = parsed.chats().get(0).chatId();
      if (chatId == null || chatId.isBlank()) {
        throw new IOException(
            "POST /chats response chats[0].chatId is blank. Body: "
                + responseBody);
      }

      return new ChatCreation(sessionId, chatId);
    }
  }

  /**
   * Computes and prints, via {@code System.out.println} (this is a manual
   * diagnostic tool, not an assertion-driven test), a summary of every
   * collected result: total attempts, success/failure counts with a breakdown
   * of failures by error description, and TTFB/total-duration percentiles in
   * milliseconds.
   *
   * @param results every simulated user's outcome, in arrival order
   * @param concurrency the configured concurrency, printed for context
   */
  private static void printSummary(final ConcurrentLinkedQueue<Result> results,
      final int concurrency) {
    List<Result> all = new ArrayList<>(results);
    List<Result> successes =
        all.stream().filter(Result::success).collect(Collectors.toList());
    List<Result> failures = all.stream().filter(result -> !result.success())
        .collect(Collectors.toList());

    List<Long> ttfbNanos = successes.stream().map(Result::timeToFirstByteNanos)
        .filter(value -> value >= 0).sorted().collect(Collectors.toList());
    List<Long> totalDurationNanos = successes.stream()
        .map(Result::totalDurationNanos).sorted().collect(Collectors.toList());
    Map<String, Long> failuresByDescription =
        failures.stream()
            .collect(
                Collectors
                    .groupingBy(
                        result -> result.errorDescription() == null ? "unknown"
                            : result.errorDescription(),
                        Collectors.counting()));

    System.out.println(
        "=== ChatStreamLoadTest summary (concurrency=" + concurrency + ") ===");
    System.out.println("Total attempts: " + all.size());
    System.out.println("Successes: " + successes.size());
    System.out.println("Failures: " + failures.size());
    failuresByDescription.forEach((description, count) -> System.out
        .println("  - " + description + ": " + count));
    System.out.printf(
        "Time-to-first-byte (ms) p50/p95/p99: %.2f / %.2f / %.2f%n",
        percentileMillis(ttfbNanos, PERCENTILE_50),
        percentileMillis(ttfbNanos, PERCENTILE_95),
        percentileMillis(ttfbNanos, PERCENTILE_99));
    System.out.printf("Total duration (ms) p50/p95/p99: %.2f / %.2f / %.2f%n",
        percentileMillis(totalDurationNanos, PERCENTILE_50),
        percentileMillis(totalDurationNanos, PERCENTILE_95),
        percentileMillis(totalDurationNanos, PERCENTILE_99));
  }

  /**
   * Computes a single percentile from a pre-sorted list of nanosecond durations
   * and converts it to milliseconds.
   *
   * @param sortedNanos durations in nanoseconds, ascending order
   * @param percentile the percentile to compute, in {@code (0, 1]}
   * @return the percentile value in milliseconds, or {@code NaN} if {@code
   *         sortedNanos} is empty
   */
  private static double percentileMillis(final List<Long> sortedNanos,
      final double percentile) {
    if (sortedNanos.isEmpty()) {
      return Double.NaN;
    }
    int index = (int) Math.ceil(percentile * sortedNanos.size()) - 1;
    int clampedIndex = Math.max(0, Math.min(index, sortedNanos.size() - 1));
    return sortedNanos.get(clampedIndex) / NANOS_PER_MILLI;
  }

  /**
   * Describes an exception as {@code SimpleClassName: message} (or just the
   * simple class name if it carries no message), used to bucket failures by
   * error type in the printed summary.
   *
   * @param e the exception to describe
   * @return a short, human-readable description of {@code e}
   */
  private static String describeError(final Exception e) {
    String message = e.getMessage();
    return message == null ? e.getClass().getSimpleName()
        : e.getClass().getSimpleName() + ": " + message;
  }

  /**
   * The session id and chat id returned by a successful anonymous chat creation
   * call.
   *
   * @param sessionId the {@value SessionHeaderResolver#X_SESSION_ID} value the
   *        backend assigned this caller
   * @param chatId the identifier of the newly created chat
   */
  private record ChatCreation(String sessionId, String chatId) {
  }

  /**
   * A single simulated user's outcome.
   *
   * @param success whether the stream completed with {@code event:done}
   * @param timeToFirstByteNanos nanoseconds from the stream request to the
   *        first non-blank line received, or {@code -1} if no line was ever
   *        received (e.g. the anonymous chat could not be created)
   * @param totalDurationNanos nanoseconds from the stream request to completion
   *        (success or failure), or {@code -1} if the stream request itself was
   *        never issued
   * @param errorDescription a short description of the failure, or {@code
   *        null} on success
   */
  private record Result(boolean success, long timeToFirstByteNanos,
      long totalDurationNanos, String errorDescription) {

    /**
     * Builds a failure result for a user who never got as far as issuing the
     * streaming request (e.g. anonymous chat creation failed).
     *
     * @param errorDescription a short description of the failure
     * @return the failure result
     */
    private static Result failedBeforeStreaming(final String errorDescription) {
      return new Result(false, -1L, -1L, errorDescription);
    }
  }

  /**
   * Minimal shape of the {@code POST /chats} JSON response this test needs:
   * just enough to extract {@code chats[0].chatId}. Ignores every other field
   * the real response carries.
   *
   * @param chats every chat in the response, in the same order the backend
   *        returned them
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ChatListResponse(List<ChatIdOnly> chats) {
  }

  /**
   * Minimal shape of a single chat within the {@code POST /chats} JSON
   * response: just its id. Ignores every other field the real chat object
   * carries.
   *
   * @param chatId the chat's identifier
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ChatIdOnly(String chatId) {
  }
}
