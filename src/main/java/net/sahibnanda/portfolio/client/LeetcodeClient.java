package net.sahibnanda.portfolio.client;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Thin client over the Leetcode GraphQL API used to fetch a user's public
 * profile statistics.
 */
@Slf4j
@Component
public final class LeetcodeClient {

  /** Media type used for all Leetcode API request bodies. */
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

  /** Path segments identifying the GraphQL endpoint. */
  private static final String[] GRAPHQL_PATH = {"graphql"};

  /** Fully resolved URL of the Leetcode GraphQL endpoint. */
  private final HttpUrl graphqlUrl;

  /** Base URL of the Leetcode API, resolved from configuration. */
  private final HttpUrl baseUrl;

  /**
   * Creates a client configured with the given Leetcode properties.
   *
   * @param properties Leetcode configuration; must not be {@code null}
   * @throws IllegalStateException if the configured base URL is blank
   * @throws NullPointerException if {@code properties} is {@code null} or its
   *         base URL cannot be parsed
   */
  public LeetcodeClient(final LeetcodeProperties properties) {

    Objects.requireNonNull(properties, "leetcodeProperties must not be null");

    if (StringUtils.isEmpty(properties.baseUrl())) {
      throw new IllegalStateException("Leetcode base URL is not configured.");
    }

    this.baseUrl = Objects.requireNonNull(HttpUrl.parse(properties.baseUrl()),
        "Invalid Leetcode base URL: " + properties.baseUrl());

    HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
    for (String segment : GRAPHQL_PATH) {
      urlBuilder.addPathSegment(segment);
    }

    this.graphqlUrl = urlBuilder.build();
  }

  /**
   * Returns the configured Leetcode base URL, with no trailing slash (e.g. for
   * building public profile links outside the GraphQL API this client itself
   * calls).
   *
   * @return the Leetcode base URL, without a trailing slash
   */
  public String getBaseUrl() {
    String url = baseUrl.toString();
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  /**
   * Fetches a Leetcode user's public profile statistics.
   *
   * @param request the profile lookup request, containing the username to look
   *        up
   * @return the user's profile statistics
   * @throws IllegalArgumentException if {@code request} is {@code null} or its
   *         username is blank
   * @throws LeetcodeCallException if the Leetcode API call fails, or the API
   *         response itself contains GraphQL errors
   */
  public LeetcodeUserProfileResponse getUserProfileDetails(
      final LeetcodeUserProfileRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request is required.");
    }

    if (StringUtils.isEmpty(request.getUsername())) {
      throw new IllegalArgumentException("username is required.");
    }

    RequestBody requestBody =
        RequestBody.create(JsonUtils.toJson(request), JSON);

    Request httpRequest =
        new Request.Builder().url(graphqlUrl).post(requestBody).build();

    try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {

      ResponseBody body = response.body();
      String responseBody = body != null ? body.string() : "";

      if (!response.isSuccessful()) {
        log.error("Leetcode API request failed. HTTP {}: {}", response.code(),
            responseBody);
        throw new LeetcodeCallException(
            String.format("Leetcode API request failed. HTTP %d: %s",
                response.code(), responseBody));
      }

      LeetcodeUserProfileResponse profileResponse =
          JsonUtils.fromJson(responseBody, LeetcodeUserProfileResponse.class);

      List<Map<String, Object>> errors = profileResponse.getErrors();
      if (errors != null && !errors.isEmpty()) {
        log.error("Leetcode API returned errors: {}", errors);
        throw new LeetcodeCallException("Leetcode API returned errors: "
            + errors.stream().map(error -> String.valueOf(error.get("message")))
                .collect(Collectors.joining("; ")));
      }

      return profileResponse;

    } catch (IOException e) {
      log.error("Failed to call Leetcode API.", e);
      throw new LeetcodeCallException("Failed to call Leetcode API.", e);
    }
  }
}
