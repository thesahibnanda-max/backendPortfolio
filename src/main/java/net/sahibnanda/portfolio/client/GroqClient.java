package net.sahibnanda.portfolio.client;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
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
