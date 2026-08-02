package net.sahibnanda.portfolio.client;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.sahibnanda.portfolio.config.LeetcodeProperties;
import net.sahibnanda.portfolio.exception.LeetcodeCallException;
import net.sahibnanda.portfolio.models.LeetcodeUserProfileRequest;
import net.sahibnanda.portfolio.models.LeetcodeUserProfileResponse;
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
public class LeetcodeClient {

  private static final MediaType JSON = MediaType.get("application/json");

  private static final OkHttpClient HTTP_CLIENT =
      new OkHttpClient.Builder()
          .connectTimeout(Duration.ofSeconds(10))
          .readTimeout(Duration.ofSeconds(60))
          .writeTimeout(Duration.ofSeconds(60))
          .build();

  private static final String[] GRAPHQL_PATH = {"graphql"};

  private final HttpUrl graphqlUrl;

  public LeetcodeClient(LeetcodeProperties properties) {

    Objects.requireNonNull(properties, "leetcodeProperties must not be null");

    if (StringUtils.isEmpty(properties.baseUrl())) {
      throw new IllegalStateException("Leetcode base URL is not configured.");
    }

    HttpUrl baseUrl =
        Objects.requireNonNull(
            HttpUrl.parse(properties.baseUrl()),
            "Invalid Leetcode base URL: " + properties.baseUrl());

    HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
    for (String segment : GRAPHQL_PATH) {
      urlBuilder.addPathSegment(segment);
    }

    this.graphqlUrl = urlBuilder.build();
  }

  public LeetcodeUserProfileResponse getUserProfileDetails(LeetcodeUserProfileRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request is required.");
    }

    if (StringUtils.isEmpty(request.getUsername())) {
      throw new IllegalArgumentException("username is required.");
    }

    RequestBody requestBody = RequestBody.create(JsonUtils.toJson(request), JSON);

    Request httpRequest = new Request.Builder().url(graphqlUrl).post(requestBody).build();

    try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {

      ResponseBody body = response.body();
      String responseBody = body != null ? body.string() : "";

      if (!response.isSuccessful()) {
        throw new LeetcodeCallException(
            String.format(
                "Leetcode API request failed. HTTP %d: %s", response.code(), responseBody));
      }

      LeetcodeUserProfileResponse profileResponse =
          JsonUtils.fromJson(responseBody, LeetcodeUserProfileResponse.class);

      List<Map<String, Object>> errors = profileResponse.getErrors();
      if (errors != null && !errors.isEmpty()) {
        throw new LeetcodeCallException(
            "Leetcode API returned errors: "
                + errors.stream()
                    .map(error -> String.valueOf(error.get("message")))
                    .collect(Collectors.joining("; ")));
      }

      return profileResponse;

    } catch (IOException e) {
      throw new LeetcodeCallException("Failed to call Leetcode API.", e);
    }
  }
}
