package net.sahibnanda.portfolio.client;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import lombok.NonNull;
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

@Component
public class GroqClient {

  private static final MediaType JSON = MediaType.get("application/json");

  private static final OkHttpClient HTTP_CLIENT =
      new OkHttpClient.Builder()
          .connectTimeout(Duration.ofSeconds(10))
          .readTimeout(Duration.ofSeconds(60))
          .writeTimeout(Duration.ofSeconds(60))
          .build();

  private static final String[] CHAT_COMPLETIONS_PATH = {"openai", "v1", "chat", "completions"};

  private final GroqProperties properties;
  private final HttpUrl chatCompletionsUrl;

  public GroqClient(GroqProperties properties) {

    Objects.requireNonNull(properties, "groqProperties must not be null");

    if (StringUtils.isEmpty(properties.baseUrl())) {
      throw new IllegalStateException("Groq base URL is not configured.");
    }

    HttpUrl baseUrl =
        Objects.requireNonNull(
            HttpUrl.parse(properties.baseUrl()), "Invalid Groq base URL: " + properties.baseUrl());

    HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
    for (String segment : CHAT_COMPLETIONS_PATH) {
      urlBuilder.addPathSegment(segment);
    }

    this.properties = properties;
    this.chatCompletionsUrl = urlBuilder.build();
  }

  public GroqCallResponse call(@NonNull GroqCallRequest request) {

    validateRequest(request);

    RequestBody requestBody = RequestBody.create(JsonUtils.toJson(request), JSON);

    Request httpRequest =
        new Request.Builder()
            .url(chatCompletionsUrl)
            .header("Authorization", "Bearer " + properties.getApiKey()) // Add the Authorization header with the API key
            .post(requestBody)
            .build();

    try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {

      ResponseBody body = response.body();
      String responseBody = body != null ? body.string() : "";

      if (!response.isSuccessful()) {
        throw new GroqCallException(
            String.format("Groq API request failed. HTTP %d: %s", response.code(), responseBody));
      }

      return JsonUtils.fromJson(responseBody, GroqCallResponse.class);

    } catch (IOException e) {
      throw new GroqCallException("Failed to call Groq API.", e);
    }
  }

  private static void validateRequest(GroqCallRequest request) {
    if (StringUtils.isEmpty(request.getModel())) {
      throw new IllegalArgumentException("model is required.");
    }
    if (request.getMessages() == null || request.getMessages().isEmpty()) {
      throw new IllegalArgumentException("messages must not be empty.");
    }
  }
}
