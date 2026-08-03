package net.sahibnanda.portfolio.objects;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * GitHub-only profile and repository stats for a single user, composed from
 * that user's public profile plus their first page of repositories.
 */
@Builder
@Data
public final class GitHubDetails {

  /** The GitHub username (login). */
  private final String username;

  /** The user's display name. */
  private final String name;

  /** URL to the user's avatar image. */
  private final String avatarUrl;

  /** The user's biography. */
  private final String bio;

  /** Number of public repositories owned by the user. */
  private final Integer publicRepos;

  /** Number of followers the user has. */
  private final Integer followers;

  /** Number of users this user follows. */
  private final Integer following;

  /** URL to the user's GitHub profile page. */
  private final String htmlUrl;

  /** Repositories owned by the user, newest-updated first. */
  private final List<RepositorySummary> repositories;

  /** A short summary of a single GitHub repository. */
  @Builder
  @Data
  public static final class RepositorySummary {

    /** The repository's name. */
    private final String name;

    /** The repository's description. */
    private final String description;

    /** URL to the repository's GitHub page. */
    private final String htmlUrl;

    /** The repository's primary programming language. */
    private final String language;

    /** Number of stars the repository has. */
    private final Integer stars;

    /** Number of forks the repository has. */
    private final Integer forks;

    /** When the repository was last updated. */
    private final Instant updatedAt;
  }
}
