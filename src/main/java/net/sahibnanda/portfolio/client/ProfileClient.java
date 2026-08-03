package net.sahibnanda.portfolio.client;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.config.ProfileProperties;
import net.sahibnanda.portfolio.exception.ProfileCallException;
import net.sahibnanda.portfolio.models.ProfileResponse;
import net.sahibnanda.portfolio.utils.JsonUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

/**
 * Thin client over a fixed, hosted JSON document used to fetch the portfolio
 * owner's profile details.
 */
@Slf4j
@Component
public final class ProfileClient {

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

  /** Full URL of the hosted profile JSON, resolved from configuration. */
  private final HttpUrl profileJsonUrl;

  /**
   * Creates a client configured with the given Profile properties.
   *
   * @param properties Profile configuration; must not be {@code null}
   * @throws IllegalStateException if the configured URL is blank
   * @throws NullPointerException if {@code properties} is {@code null} or its
   *         URL cannot be parsed
   */
  public ProfileClient(final ProfileProperties properties) {

    Objects.requireNonNull(properties, "profileProperties must not be null");

    if (StringUtils.isEmpty(properties.profileJSONRetreivalURL())) {
      throw new IllegalStateException("Profile JSON URL is not configured.");
    }

    this.profileJsonUrl = Objects.requireNonNull(
        HttpUrl.parse(properties.profileJSONRetreivalURL()),
        "Invalid profile JSON URL: " + properties.profileJSONRetreivalURL());
  }

  /**
   * Fetches the portfolio owner's profile details.
   *
   * @return the deserialized profile document
   * @throws ProfileCallException if the request fails or the API returns a
   *         non-successful HTTP status
   */
  public ProfileResponse getProfile() {

    Request httpRequest =
        new Request.Builder().url(profileJsonUrl).get().build();

    try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {

      ResponseBody body = response.body();
      String responseBody = body != null ? body.string() : "";

      if (!response.isSuccessful()) {
        log.error("Profile API request failed. HTTP {}: {}", response.code(),
            responseBody);
        throw new ProfileCallException(
            String.format("Profile API request failed. HTTP %d: %s",
                response.code(), responseBody));
      }

      return JsonUtils.fromJson(responseBody, ProfileResponse.class);

    } catch (IOException e) {
      log.error("Failed to call Profile API.", e);
      throw new ProfileCallException("Failed to call Profile API.", e);
    }
  }
}
