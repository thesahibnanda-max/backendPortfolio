package net.sahibnanda.portfolio.client;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.config.GitHubProperties;
import net.sahibnanda.portfolio.exception.GitHubCallException;
import net.sahibnanda.portfolio.models.GitHubCommit;
import net.sahibnanda.portfolio.models.GitHubRepository;
import net.sahibnanda.portfolio.models.GitHubUser;
import net.sahibnanda.portfolio.utils.JsonUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

/**
 * Thin client over the public GitHub REST API used to fetch user profiles,
 * repositories and commits.
 */
@Slf4j
@Component
public final class GitHubClient {

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

  /** HTTP header name used to negotiate the response media type. */
  private static final String HEADER_ACCEPT_NAME = "Accept";

  /** HTTP header value requesting the GitHub JSON media type. */
  private static final String HEADER_ACCEPT_VALUE =
      "application/vnd.github+json";

  /** HTTP header name used to pin the GitHub API version. */
  private static final String HEADER_API_VERSION_NAME = "X-GitHub-Api-Version";

  /** GitHub API version requested via the version header. */
  private static final String HEADER_API_VERSION_VALUE = "2022-11-28";

  /** GitHub API path segment for the users endpoint. */
  private static final String PATH_USERS = "users";

  /** GitHub API path segment for the repositories endpoint. */
  private static final String PATH_REPOS = "repos";

  /** GitHub API path segment for the commits endpoint. */
  private static final String PATH_COMMITS = "commits";

  /** Query parameter name controlling the result sort field. */
  private static final String QUERY_PARAM_SORT = "sort";

  /** Query parameter name controlling the result sort direction. */
  private static final String QUERY_PARAM_DIRECTION = "direction";

  /** Query parameter name controlling the page size. */
  private static final String QUERY_PARAM_PER_PAGE = "per_page";

  /** Query parameter name controlling the page number. */
  private static final String QUERY_PARAM_PAGE = "page";

  /** Query parameter name filtering commits by author. */
  private static final String QUERY_PARAM_AUTHOR = "author";

  /** Sort field value indicating sort by last updated. */
  private static final String SORT_UPDATED = "updated";

  /** Sort direction value indicating descending order. */
  private static final String DIRECTION_DESC = "desc";

  /** Default number of results requested per page. */
  private static final int DEFAULT_PER_PAGE = 100;

  /** Base URL of the GitHub API, resolved from configuration. */
  private final HttpUrl baseUrl;

  /**
   * Creates a client configured with the given GitHub properties.
   *
   * @param properties GitHub configuration; must not be {@code null}
   * @throws IllegalStateException if the configured base URL is blank
   * @throws NullPointerException if {@code properties} is {@code null} or its
   *         base URL cannot be parsed
   */
  public GitHubClient(final GitHubProperties properties) {

    Objects.requireNonNull(properties, "gitHubProperties must not be null");

    if (StringUtils.isEmpty(properties.baseUrl())) {
      throw new IllegalStateException("GitHub base URL is not configured.");
    }

    this.baseUrl = Objects.requireNonNull(HttpUrl.parse(properties.baseUrl()),
        "Invalid GitHub base URL: " + properties.baseUrl());
  }

  /**
   * Fetches the public profile details for a GitHub user.
   *
   * @param username the GitHub username to look up
   * @return the user's profile details
   * @throws IllegalArgumentException if {@code username} is blank
   * @throws GitHubCallException if the GitHub API call fails
   */
  public GitHubUser getUserDetails(final String username) {

    if (StringUtils.isEmpty(username)) {
      throw new IllegalArgumentException("username is required.");
    }

    HttpUrl url = baseUrl.newBuilder().addPathSegment(PATH_USERS)
        .addPathSegment(username).build();

    return execute(url, GitHubUser.class);
  }

  /**
   * Lists repositories owned by a GitHub user, sorted by most recently updated
   * first.
   *
   * @param username the GitHub username to look up
   * @param page the page number to request
   * @return the repositories on the requested page
   * @throws IllegalArgumentException if {@code username} is blank
   * @throws GitHubCallException if the GitHub API call fails
   */
  public List<GitHubRepository> listUserRepositories(final String username,
      final int page) {

    if (StringUtils.isEmpty(username)) {
      throw new IllegalArgumentException("username is required.");
    }

    HttpUrl url = baseUrl.newBuilder().addPathSegment(PATH_USERS)
        .addPathSegment(username).addPathSegment(PATH_REPOS)
        .addQueryParameter(QUERY_PARAM_SORT, SORT_UPDATED)
        .addQueryParameter(QUERY_PARAM_DIRECTION, DIRECTION_DESC)
        .addQueryParameter(QUERY_PARAM_PER_PAGE,
            String.valueOf(DEFAULT_PER_PAGE))
        .addQueryParameter(QUERY_PARAM_PAGE, String.valueOf(page)).build();

    return execute(url, new TypeReference<List<GitHubRepository>>() {
    });
  }

  /**
   * Lists commits in a repository made by a specific author.
   *
   * @param owner the repository owner
   * @param repo the repository name
   * @param author the commit author to filter by
   * @param page the page number to request
   * @return the commits on the requested page
   * @throws IllegalArgumentException if any argument is blank
   * @throws GitHubCallException if the GitHub API call fails
   */
  public List<GitHubCommit> listCommitsByAuthor(final String owner,
      final String repo, final String author, final int page) {

    if (StringUtils.isEmpty(owner)) {
      throw new IllegalArgumentException("owner is required.");
    }
    if (StringUtils.isEmpty(repo)) {
      throw new IllegalArgumentException("repo is required.");
    }
    if (StringUtils.isEmpty(author)) {
      throw new IllegalArgumentException("author is required.");
    }

    HttpUrl url = baseUrl.newBuilder().addPathSegment(PATH_REPOS)
        .addPathSegment(owner).addPathSegment(repo).addPathSegment(PATH_COMMITS)
        .addQueryParameter(QUERY_PARAM_AUTHOR, author)
        .addQueryParameter(QUERY_PARAM_PER_PAGE,
            String.valueOf(DEFAULT_PER_PAGE))
        .addQueryParameter(QUERY_PARAM_PAGE, String.valueOf(page)).build();

    return execute(url, new TypeReference<List<GitHubCommit>>() {
    });
  }

  /**
   * Fetches a single commit by its SHA.
   *
   * @param owner the repository owner
   * @param repo the repository name
   * @param commitSha the commit SHA to look up
   * @return the requested commit
   * @throws IllegalArgumentException if any argument is blank
   * @throws GitHubCallException if the GitHub API call fails
   */
  public GitHubCommit getCommit(final String owner, final String repo,
      final String commitSha) {

    if (StringUtils.isEmpty(owner)) {
      throw new IllegalArgumentException("owner is required.");
    }
    if (StringUtils.isEmpty(repo)) {
      throw new IllegalArgumentException("repo is required.");
    }
    if (StringUtils.isEmpty(commitSha)) {
      throw new IllegalArgumentException("commitSha is required.");
    }

    HttpUrl url = baseUrl.newBuilder().addPathSegment(PATH_REPOS)
        .addPathSegment(owner).addPathSegment(repo).addPathSegment(PATH_COMMITS)
        .addPathSegment(commitSha).build();

    return execute(url, GitHubCommit.class);
  }

  /**
   * Executes a GET request against the GitHub API and deserializes the JSON
   * response into a plain (non-generic) POJO type.
   *
   * <p>
   * Prefer this overload whenever the response type has no generic parameters
   * (e.g. {@code GitHubUser}, {@code
   * GitHubCommit}) — a {@code Class<T>} token is all Jackson needs in that
   * case, avoiding the anonymous-subclass allocation a {@link TypeReference}
   * requires purely to work around Java's type erasure.
   *
   * @param url the fully built request URL
   * @param responseType the class to deserialize the response into
   * @param <T> the deserialized response type
   * @return the deserialized response body
   * @throws GitHubCallException if the request fails or the API returns a
   *         non-successful HTTP status
   */
  private <T> T execute(final HttpUrl url, final Class<T> responseType) {
    return JsonUtils.fromJson(executeRaw(url), responseType);
  }

  /**
   * Executes a GET request against the GitHub API and deserializes the JSON
   * response into a generic type (e.g. {@code
   * List<GitHubRepository>}).
   *
   * <p>
   * Use this overload only when the response type is itself generic and
   * therefore cannot be represented by a {@code
   * Class<T>} token alone, due to Java's type erasure.
   *
   * @param url the fully built request URL
   * @param responseType the type reference to deserialize the response into
   * @param <T> the deserialized response type
   * @return the deserialized response body
   * @throws GitHubCallException if the request fails or the API returns a
   *         non-successful HTTP status
   */
  private <T> T execute(final HttpUrl url,
      final TypeReference<T> responseType) {
    return JsonUtils.fromJson(executeRaw(url), responseType);
  }

  /**
   * Sends a GET request against the GitHub API with the required headers and
   * returns the raw JSON response body. Both {@code
   * execute} overloads above delegate here, so the HTTP call and HTTP-status
   * error handling live in exactly one place regardless of which
   * deserialization strategy the caller needs.
   *
   * @param url the fully built request URL
   * @return the raw JSON response body
   * @throws GitHubCallException if the request fails or the API returns a
   *         non-successful HTTP status
   */
  private String executeRaw(final HttpUrl url) {

    Request httpRequest = new Request.Builder().url(url)
        .header(HEADER_ACCEPT_NAME, HEADER_ACCEPT_VALUE)
        .header(HEADER_API_VERSION_NAME, HEADER_API_VERSION_VALUE).get()
        .build();

    try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {

      ResponseBody body = response.body();
      String responseBody = body != null ? body.string() : "";

      if (!response.isSuccessful()) {
        log.error("GitHub API call to {} failed. HTTP {}: {}", url,
            response.code(), responseBody);
        throw new GitHubCallException(
            String.format("GitHub API request failed. HTTP %d: %s",
                response.code(), responseBody));
      }

      return responseBody;

    } catch (IOException e) {
      log.error("Failed to call GitHub API at {}.", url, e);
      throw new GitHubCallException("Failed to call GitHub API.", e);
    }
  }
}
