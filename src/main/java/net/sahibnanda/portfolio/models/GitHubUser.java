package net.sahibnanda.portfolio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/** GitHub user or organization account, as returned by the GitHub API. */
@Builder
@Jacksonized
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class GitHubUser {

  /** Username (login) of the GitHub account. */
  private String login;

  /** Unique numeric identifier of the account. */
  private Long id;

  /** Global node ID of the account (GraphQL). */
  private String nodeId;

  /** URL of the account's avatar image. */
  private String avatarUrl;

  /** Gravatar ID associated with the account. */
  private String gravatarId;

  /** API URL for this user resource. */
  private String url;

  /** URL of the account's GitHub profile page. */
  private String htmlUrl;

  /** API URL listing this account's followers. */
  private String followersUrl;

  /** API URL listing accounts this account follows. */
  private String followingUrl;

  /** API URL for this account's gists. */
  private String gistsUrl;

  /** API URL for repositories starred by this account. */
  private String starredUrl;

  /** API URL for this account's subscriptions. */
  private String subscriptionsUrl;

  /** API URL for this account's organizations. */
  private String organizationsUrl;

  /** API URL for this account's repositories. */
  private String reposUrl;

  /** API URL for this account's public events. */
  private String eventsUrl;

  /** API URL for events received by this account. */
  private String receivedEventsUrl;

  /** Account type, e.g. "User" or "Organization". */
  private String type;

  /** Viewer-relative type of the account, if known. */
  private String userViewType;

  /** Whether the user has GitHub site admin privileges. */
  private Boolean siteAdmin;

  /** Display name of the user. */
  private String name;

  /** Company associated with the user's profile. */
  private String company;

  /** URL of the user's personal blog or website. */
  private String blog;

  /** Geographic location listed on the user's profile. */
  private String location;

  /** Public email address of the user. */
  private String email;

  /** Email used for notifications, if configured. */
  private String notificationEmail;

  /** Whether the user is open to being hired. */
  private Boolean hireable;

  /** Short biography text from the user's profile. */
  private String bio;

  /** Twitter/X username listed on the user's profile. */
  private String twitterUsername;

  /** Number of public repositories owned by the user. */
  private Integer publicRepos;

  /** Number of public gists owned by the user. */
  private Integer publicGists;

  /** Number of accounts following this user. */
  private Integer followers;

  /** Number of accounts this user is following. */
  private Integer following;

  /** Timestamp when the account was created. */
  private Instant createdAt;

  /** Timestamp when the account was last updated. */
  private Instant updatedAt;

  /** Billing plan associated with the account. */
  private Plan plan;

  /** Timestamp when the user starred a resource. */
  private String starredAt;

  /** LDAP distinguished name for enterprise accounts. */
  private String ldapDn;

  /** Whether the account has GitHub Business Plus. */
  private Boolean businessPlus;

  /** Billing plan details for a GitHub account. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static final class Plan {

    /** Number of collaborators allowed under the plan. */
    private Integer collaborators;

    /** Name of the billing plan. */
    private String name;

    /** Disk space, in kilobytes, included in the plan. */
    private Integer space;

    /** Number of private repositories allowed. */
    private Integer privateRepos;
  }
}
