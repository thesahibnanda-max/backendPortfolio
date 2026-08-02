package net.sahibnanda.portfolio.client;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
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

@Component
public class GitHubClient {

  private static final OkHttpClient HTTP_CLIENT =
      new OkHttpClient.Builder()
          .connectTimeout(Duration.ofSeconds(10))
          .readTimeout(Duration.ofSeconds(60))
          .writeTimeout(Duration.ofSeconds(60))
          .build();

  private static final String HEADER_ACCEPT_NAME = "Accept";
  private static final String HEADER_ACCEPT_VALUE = "application/vnd.github+json";
  private static final String HEADER_API_VERSION_NAME = "X-GitHub-Api-Version";
  private static final String HEADER_API_VERSION_VALUE = "2022-11-28";

  private static final String PATH_USERS = "users";
  private static final String PATH_REPOS = "repos";
  private static final String PATH_COMMITS = "commits";

  private static final String QUERY_PARAM_SORT = "sort";
  private static final String QUERY_PARAM_DIRECTION = "direction";
  private static final String QUERY_PARAM_PER_PAGE = "per_page";
  private static final String QUERY_PARAM_PAGE = "page";
  private static final String QUERY_PARAM_AUTHOR = "author";

  private static final String SORT_UPDATED = "updated";
  private static final String DIRECTION_DESC = "desc";
  private static final int DEFAULT_PER_PAGE = 100;

  private final HttpUrl baseUrl;

  public GitHubClient(GitHubProperties properties) {

    Objects.requireNonNull(properties, "gitHubProperties must not be null");

    if (StringUtils.isEmpty(properties.baseUrl())) {
      throw new IllegalStateException("GitHub base URL is not configured.");
    }

    this.baseUrl =
        Objects.requireNonNull(
            HttpUrl.parse(properties.baseUrl()),
            "Invalid GitHub base URL: " + properties.baseUrl());
  }

  public GitHubUser getUserDetails(String username) {

    if (StringUtils.isEmpty(username)) {
      throw new IllegalArgumentException("username is required.");
    }

    HttpUrl url = baseUrl.newBuilder().addPathSegment(PATH_USERS).addPathSegment(username).build();

    return execute(url, new TypeReference<GitHubUser>() {});
  }

  public List<GitHubRepository> listUserRepositories(String username, int page) {

    if (StringUtils.isEmpty(username)) {
      throw new IllegalArgumentException("username is required.");
    }

    HttpUrl url =
        baseUrl
            .newBuilder()
            .addPathSegment(PATH_USERS)
            .addPathSegment(username)
            .addPathSegment(PATH_REPOS)
            .addQueryParameter(QUERY_PARAM_SORT, SORT_UPDATED)
            .addQueryParameter(QUERY_PARAM_DIRECTION, DIRECTION_DESC)
            .addQueryParameter(QUERY_PARAM_PER_PAGE, String.valueOf(DEFAULT_PER_PAGE))
            .addQueryParameter(QUERY_PARAM_PAGE, String.valueOf(page))
            .build();

    return execute(url, new TypeReference<List<GitHubRepository>>() {});
  }

  public List<GitHubCommit> listCommitsByAuthor(
      String owner, String repo, String author, int page) {

    if (StringUtils.isEmpty(owner)) {
      throw new IllegalArgumentException("owner is required.");
    }
    if (StringUtils.isEmpty(repo)) {
      throw new IllegalArgumentException("repo is required.");
    }
    if (StringUtils.isEmpty(author)) {
      throw new IllegalArgumentException("author is required.");
    }

    HttpUrl url =
        baseUrl
            .newBuilder()
            .addPathSegment(PATH_REPOS)
            .addPathSegment(owner)
            .addPathSegment(repo)
            .addPathSegment(PATH_COMMITS)
            .addQueryParameter(QUERY_PARAM_AUTHOR, author)
            .addQueryParameter(QUERY_PARAM_PER_PAGE, String.valueOf(DEFAULT_PER_PAGE))
            .addQueryParameter(QUERY_PARAM_PAGE, String.valueOf(page))
            .build();

    return execute(url, new TypeReference<List<GitHubCommit>>() {});
  }

  public GitHubCommit getCommit(String owner, String repo, String commitSha) {

    if (StringUtils.isEmpty(owner)) {
      throw new IllegalArgumentException("owner is required.");
    }
    if (StringUtils.isEmpty(repo)) {
      throw new IllegalArgumentException("repo is required.");
    }
    if (StringUtils.isEmpty(commitSha)) {
      throw new IllegalArgumentException("commitSha is required.");
    }

    HttpUrl url =
        baseUrl
            .newBuilder()
            .addPathSegment(PATH_REPOS)
            .addPathSegment(owner)
            .addPathSegment(repo)
            .addPathSegment(PATH_COMMITS)
            .addPathSegment(commitSha)
            .build();

    return execute(url, new TypeReference<GitHubCommit>() {});
  }

  private <T> T execute(HttpUrl url, TypeReference<T> responseType) {

    Request httpRequest =
        new Request.Builder()
            .url(url)
            .header(HEADER_ACCEPT_NAME, HEADER_ACCEPT_VALUE)
            .header(HEADER_API_VERSION_NAME, HEADER_API_VERSION_VALUE)
            .get()
            .build();

    try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {

      ResponseBody body = response.body();
      String responseBody = body != null ? body.string() : "";

      if (!response.isSuccessful()) {
        throw new GitHubCallException(
            String.format("GitHub API request failed. HTTP %d: %s", response.code(), responseBody));
      }

      return JsonUtils.fromJson(responseBody, responseType);

    } catch (IOException e) {
      throw new GitHubCallException("Failed to call GitHub API.", e);
    }
  }
}
