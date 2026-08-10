package net.sahibnanda.portfolio.client;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.config.GroqProperties;
import net.sahibnanda.portfolio.exception.GroqCallException;
import net.sahibnanda.portfolio.models.GroqCallRequest;
import net.sahibnanda.portfolio.models.GroqCallResponse;
import net.sahibnanda.portfolio.utils.JsonUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.stereotype.Component;

/**
 * Thin client over the Groq chat completions API used to generate AI-powered
 * summaries for the portfolio.
 */
@Slf4j
@Component
public final class GroqClient {

  /** Media type used for all Groq API request bodies. */
  private static final MediaType JSON = MediaType.get("application/json");

  /** Connect timeout, in seconds, for the shared HTTP client. */
  private static final int CONNECT_TIMEOUT_SECONDS = 10;

  /** Read timeout, in seconds, for the shared HTTP client. */
  private static final int READ_TIMEOUT_SECONDS = 60;

  /** Write timeout, in seconds, for the shared HTTP client. */
  private static final int WRITE_TIMEOUT_SECONDS = 60;

  /**
   * Shared OkHttp client used for all requests, with fixed connect/read/write
   * timeouts.
   */
  private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
      .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
      .readTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
      .writeTimeout(Duration.ofSeconds(WRITE_TIMEOUT_SECONDS)).build();

  /** Path segments identifying the chat completions endpoint. */
  private static final String[] CHAT_COMPLETIONS_PATH =
      {"openai", "v1", "chat", "completions"};

  /** Prefix identifying a data line within a Groq SSE stream. */
  private static final String SSE_DATA_PREFIX = "data: ";

  /** Sentinel payload marking the end of a Groq SSE stream. */
  private static final String SSE_DONE_SENTINEL = "[DONE]";

  /** Groq configuration this client was constructed with. */
  private final GroqProperties properties;

  /** Fully resolved URL of the chat completions endpoint. */
  private final HttpUrl chatCompletionsUrl;

  /**
   * Creates a client configured with the given Groq properties.
   *
   * @param groqProperties Groq configuration; must not be {@code null}
   * @throws IllegalStateException if the configured base URL is blank
   * @throws NullPointerException if {@code groqProperties} is {@code null} or
   *         its base URL cannot be parsed
   */
  public GroqClient(final GroqProperties groqProperties) {

    Objects.requireNonNull(groqProperties, "groqProperties must not be null");

    if (StringUtils.isEmpty(groqProperties.baseUrl())) {
      throw new IllegalStateException("Groq base URL is not configured.");
    }

    HttpUrl baseUrl =
        Objects.requireNonNull(HttpUrl.parse(groqProperties.baseUrl()),
            "Invalid Groq base URL: " + groqProperties.baseUrl());

    HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
    for (String segment : CHAT_COMPLETIONS_PATH) {
      urlBuilder.addPathSegment(segment);
    }

    this.properties = groqProperties;
    this.chatCompletionsUrl = urlBuilder.build();
  }

  /**
   * Sends a chat completion request to the Groq API.
   *
   * @param request the chat completion request to send
   * @return the Groq API's chat completion response
   * @throws IllegalArgumentException if the request is missing a model or has
   *         no messages
   * @throws GroqCallException if the Groq API call fails
   */
  public GroqCallResponse call(@NonNull final GroqCallRequest request) {

    validateRequest(request);

    RequestBody requestBody =
        RequestBody.create(JsonUtils.toJson(request), JSON);

    Request httpRequest = new Request.Builder().url(chatCompletionsUrl)
        // Add the Authorization header with the API key
        .header("Authorization", "Bearer " + properties.getApiKey())
        .post(requestBody).build();

    try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {

      ResponseBody body = response.body();
      String responseBody = body != null ? body.string() : "";

      if (!response.isSuccessful()) {
        log.error("Groq API request failed. HTTP {}: {}", response.code(),
            responseBody);
        throw new GroqCallException(
            String.format("Groq API request failed. HTTP %d: %s",
                response.code(), responseBody));
      }

      return JsonUtils.fromJson(responseBody, GroqCallResponse.class);

    } catch (IOException e) {
      log.error("Failed to call Groq API.", e);
      throw new GroqCallException("Failed to call Groq API.", e);
    }
  }

  /**
   * Sends a chat completion request to the Groq API and streams the response as
   * it arrives over Server-Sent Events (SSE).
   *
   * @param request the chat completion request to send
   * @param onDelta callback invoked once per non-empty content chunk received
   *        from the stream, in arrival order
   * @return the full completion text, formed by concatenating every content
   *         chunk delivered to {@code onDelta}
   * @throws IllegalArgumentException if the request is missing a model or has
   *         no messages
   * @throws GroqCallException if the Groq API call fails, or if the stream ends
   *         (end of input) without ever delivering the {@code [DONE]} sentinel
   *         -- treating a mid-stream Groq failure as a complete answer would
   *         let a truncated response be persisted as if it were whole
   */
  public String callStream(@NonNull final GroqCallRequest request,
      @NonNull final Consumer<String> onDelta) {

    validateRequest(request);

    RequestBody requestBody =
        RequestBody.create(JsonUtils.toJson(request), JSON);

    Request httpRequest = new Request.Builder().url(chatCompletionsUrl)
        // Add the Authorization header with the API key
        .header("Authorization", "Bearer " + properties.getApiKey())
        .post(requestBody).build();

    try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {

      if (!response.isSuccessful()) {
        ResponseBody body = response.body();
        String responseBody = body != null ? body.string() : "";
        log.error("Groq API request failed. HTTP {}: {}", response.code(),
            responseBody);
        throw new GroqCallException(
            String.format("Groq API request failed. HTTP %d: %s",
                response.code(), responseBody));
      }

      StringBuilder accumulated = new StringBuilder();
      ResponseBody body = response.body();
      BufferedSource source = body != null ? body.source() : null;

      boolean sawDoneSentinel = false;
      if (source != null) {
        String line;
        while ((line = source.readUtf8Line()) != null) {

          if (!line.startsWith(SSE_DATA_PREFIX)) {
            continue;
          }

          String payload = line.substring(SSE_DATA_PREFIX.length());
          if (SSE_DONE_SENTINEL.equals(payload)) {
            sawDoneSentinel = true;
            break;
          }

          GroqCallResponse chunk =
              JsonUtils.fromJson(payload, GroqCallResponse.class);
          String content = extractDeltaContent(chunk);
          // Deliberately not StringUtils.isEmpty(content): that method
          // treats a whitespace-only string as empty, but a delta chunk
          // consisting of a single standalone space is real, meaningful
          // content (commonly the space Groq emits immediately before a
          // digit-sequence token) -- collapsing it here silently glued
          // words to the following number (e.g. "is1832" instead of
          // "is 1832").
          if (content != null && !content.isEmpty()) {
            accumulated.append(content);
            onDelta.accept(content);
          }
        }
      }

      if (!sawDoneSentinel) {
        log.error(
            "Groq stream for chat ended before completion (no [DONE] "
                + "sentinel); {} chars of partial content discarded.",
            accumulated.length());
        throw new GroqCallException("Groq stream ended before completion.");
      }

      return accumulated.toString();

    } catch (IOException e) {
      log.error("Failed to stream from Groq API.", e);
      throw new GroqCallException("Failed to stream from Groq API.", e);
    }
  }

  /**
   * Extracts the incremental delta content from a single streamed chat
   * completion chunk, tolerating chunks whose {@code choices}, {@code delta},
   * or {@code content} are absent.
   *
   * @param chunk the streamed chat completion chunk to inspect
   * @return the delta content, or {@code null} if the chunk carries none
   */
  private static String extractDeltaContent(final GroqCallResponse chunk) {
    if (chunk == null || chunk.getChoices() == null
        || chunk.getChoices().isEmpty()) {
      return null;
    }
    GroqCallResponse.Delta delta = chunk.getChoices().get(0).getDelta();
    return delta != null ? delta.getContent() : null;
  }

  /**
   * Validates that a chat completion request has a model and at least one
   * message.
   *
   * @param request the request to validate
   * @throws IllegalArgumentException if the request is missing a model or has
   *         no messages
   */
  private static void validateRequest(final GroqCallRequest request) {
    if (StringUtils.isEmpty(request.getModel())) {
      throw new IllegalArgumentException("model is required.");
    }
    if (request.getMessages() == null || request.getMessages().isEmpty()) {
      throw new IllegalArgumentException("messages must not be empty.");
    }
  }
}
