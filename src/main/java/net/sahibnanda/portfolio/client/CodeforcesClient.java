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

@Component
public class CodeforcesClient {

  private static final OkHttpClient HTTP_CLIENT =
      new OkHttpClient.Builder()
          .connectTimeout(Duration.ofSeconds(10))
          .readTimeout(Duration.ofSeconds(60))
          .writeTimeout(Duration.ofSeconds(60))
          .build();

  private static final String[] USER_RATING_PATH = {"api", "user.rating"};
  private static final String STATUS_OK = "OK";

  private final HttpUrl baseUrl;

  public CodeforcesClient(CodeforcesProperties properties) {

    Objects.requireNonNull(properties, "codeforcesProperties must not be null");

    if (StringUtils.isEmpty(properties.baseUrl())) {
      throw new IllegalStateException("Codeforces base URL is not configured.");
    }

    this.baseUrl =
        Objects.requireNonNull(
            HttpUrl.parse(properties.baseUrl()),
            "Invalid Codeforces base URL: " + properties.baseUrl());
  }

  public CodeforcesUserRatingResponse getUserRatingDetails(String username) {

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
            String.format(
                "Codeforces API request failed. HTTP %d: %s", response.code(), responseBody));
      }

      CodeforcesUserRatingResponse ratingResponse =
          JsonUtils.fromJson(responseBody, CodeforcesUserRatingResponse.class);

      if (!STATUS_OK.equals(ratingResponse.getStatus())) {
        throw new CodeforcesCallException(
            ratingResponse.getComment() != null
                ? ratingResponse.getComment()
                : "Codeforces API returned a non-OK status: " + ratingResponse.getStatus());
      }

      return ratingResponse;

    } catch (IOException e) {
      throw new CodeforcesCallException("Failed to call Codeforces API.", e);
    }
  }
}
