package net.sahibnanda.portfolio.client;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import net.sahibnanda.portfolio.config.CodeforcesProperties;
import net.sahibnanda.portfolio.exception.CodeforcesCallException;
import net.sahibnanda.portfolio.models.CodeforcesUserRatingResponse;
import net.sahibnanda.portfolio.utils.JsonUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

/**
 * Thin client over the Codeforces public API used to fetch a user's contest
 * rating history.
 */
@Component
public final class CodeforcesClient {

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

  /** Path segments identifying the user rating endpoint. */
  private static final String[] USER_RATING_PATH = {"api", "user.rating"};

  /** Status value indicating a successful Codeforces API response. */
  private static final String STATUS_OK = "OK";

  /** Base URL of the Codeforces API, resolved from configuration. */
  private final HttpUrl baseUrl;

  /**
   * Creates a client configured with the given Codeforces properties.
   *
   * @param properties Codeforces configuration; must not be {@code null}
   * @throws IllegalStateException if the configured base URL is blank
   * @throws NullPointerException if {@code properties} is {@code null} or its
   *         base URL cannot be parsed
   */
  public CodeforcesClient(final CodeforcesProperties properties) {

    Objects.requireNonNull(properties, "codeforcesProperties must not be null");

    if (StringUtils.isEmpty(properties.baseUrl())) {
      throw new IllegalStateException("Codeforces base URL is not configured.");
    }

    this.baseUrl = Objects.requireNonNull(HttpUrl.parse(properties.baseUrl()),
        "Invalid Codeforces base URL: " + properties.baseUrl());
  }

  /**
   * Fetches a Codeforces user's contest rating history.
   *
   * @param username the Codeforces handle to look up
   * @return the user's rating history
   * @throws IllegalArgumentException if {@code username} is blank
   * @throws CodeforcesCallException if the Codeforces API call fails, or the
   *         API responds with a non-OK status
   */
  public CodeforcesUserRatingResponse getUserRatingDetails(
      final String username) {

    if (StringUtils.isEmpty(username)) {
      throw new IllegalArgumentException("username is required.");
    }

    HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
    for (String segment : USER_RATING_PATH) {
      urlBuilder.addPathSegment(segment);
    }
    HttpUrl url = urlBuilder.addQueryParameter("handle", username).build();

    Request httpRequest = new Request.Builder().url(url).get().build();

    try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {

      ResponseBody body = response.body();
      String responseBody = body != null ? body.string() : "";

      if (!response.isSuccessful()) {
        throw new CodeforcesCallException(
            String.format("Codeforces API request failed. HTTP %d: %s",
                response.code(), responseBody));
      }

      CodeforcesUserRatingResponse ratingResponse =
          JsonUtils.fromJson(responseBody, CodeforcesUserRatingResponse.class);

      if (!STATUS_OK.equals(ratingResponse.getStatus())) {
        throw new CodeforcesCallException(
            ratingResponse.getComment() != null ? ratingResponse.getComment()
                : "Codeforces API returned a non-OK status: "
                    + ratingResponse.getStatus());
      }

      return ratingResponse;

    } catch (IOException e) {
      throw new CodeforcesCallException("Failed to call Codeforces API.", e);
    }
  }
}
