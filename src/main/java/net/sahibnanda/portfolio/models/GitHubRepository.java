package net.sahibnanda.portfolio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/** A GitHub repository, as returned by the GitHub REST API. */
@Builder
@Jacksonized
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class GitHubRepository {

  /** Unique numeric identifier of the repository. */
  private Long id;

  /** Global node ID of the repository (GraphQL). */
  private String nodeId;

  /** Short name of the repository. */
  private String name;

  /** Full name of the repository, in "owner/name" form. */
  private String fullName;

  /** GitHub account that owns the repository. */
  private GitHubUser owner;

  /** Whether the repository is private. */
  @JsonProperty("private")
  private Boolean isPrivate;

  /** URL of the repository's GitHub web page. */
  private String htmlUrl;

  /** Short description of the repository. */
  private String description;

  /** Whether the repository is a fork of another repository. */
  private Boolean fork;

  /** API URL for this repository resource. */
  private String url;

  /** API URL for the repository's archive links. */
  private String archiveUrl;

  /** API URL for the repository's assignees. */
  private String assigneesUrl;

  /** API URL for the repository's Git blobs. */
  private String blobsUrl;

  /** API URL for the repository's branches. */
  private String branchesUrl;

  /** API URL for the repository's collaborators. */
  private String collaboratorsUrl;

  /** API URL for the repository's issue/commit comments. */
  private String commentsUrl;

  /** API URL for the repository's commits. */
  private String commitsUrl;

  /** API URL for comparing commits in the repository. */
  private String compareUrl;

  /** API URL for the repository's file contents. */
  private String contentsUrl;

  /** API URL for the repository's contributors. */
  private String contributorsUrl;

  /** API URL for the repository's deployments. */
  private String deploymentsUrl;

  /** API URL for the repository's downloads. */
  private String downloadsUrl;

  /** API URL for the repository's events. */
  private String eventsUrl;

  /** API URL for the repository's forks. */
  private String forksUrl;

  /** API URL for the repository's Git commits. */
  private String gitCommitsUrl;

  /** API URL for the repository's Git references. */
  private String gitRefsUrl;

  /** API URL for the repository's Git tags. */
  private String gitTagsUrl;

  /** API URL for a single issue comment. */
  private String issueCommentUrl;

  /** API URL for the repository's issue events. */
  private String issueEventsUrl;

  /** API URL for the repository's issues. */
  private String issuesUrl;

  /** API URL for the repository's deploy keys. */
  private String keysUrl;

  /** API URL for the repository's labels. */
  private String labelsUrl;

  /** API URL for the repository's detected languages. */
  private String languagesUrl;

  /** API URL for merging branches in the repository. */
  private String mergesUrl;

  /** API URL for the repository's milestones. */
  private String milestonesUrl;

  /** API URL for the repository's notifications. */
  private String notificationsUrl;

  /** API URL for the repository's pull requests. */
  private String pullsUrl;

  /** API URL for the repository's releases. */
  private String releasesUrl;

  /** API URL for the repository's stargazers. */
  private String stargazersUrl;

  /** API URL for the repository's commit statuses. */
  private String statusesUrl;

  /** API URL for the repository's subscribers. */
  private String subscribersUrl;

  /** API URL for the authenticated user's subscription. */
  private String subscriptionUrl;

  /** API URL for the repository's tags. */
  private String tagsUrl;

  /** API URL for the repository's teams. */
  private String teamsUrl;

  /** API URL for the repository's Git trees. */
  private String treesUrl;

  /** API URL for the repository's webhooks. */
  private String hooksUrl;

  /** Git protocol URL for cloning the repository. */
  private String gitUrl;

  /** SSH URL for cloning the repository. */
  private String sshUrl;

  /** HTTPS URL for cloning the repository. */
  private String cloneUrl;

  /** SVN URL for accessing the repository. */
  private String svnUrl;

  /** URL of the upstream repository this one mirrors. */
  private String mirrorUrl;

  /** URL of the repository's homepage or project site. */
  private String homepage;

  /** Primary programming language of the repository. */
  private String language;

  /** Number of forks of this repository. */
  private Integer forksCount;

  /** Number of users who have starred this repository. */
  private Integer stargazersCount;

  /** Number of users watching this repository. */
  private Integer watchersCount;

  /** Size of the repository, in kilobytes. */
  private Integer size;

  /** Name of the repository's default branch. */
  private String defaultBranch;

  /** Number of open issues and pull requests. */
  private Integer openIssuesCount;

  /** Number of forks of this repository. */
  private Integer forks;

  /** Number of open issues in the repository. */
  private Integer openIssues;

  /** Number of users watching this repository. */
  private Integer watchers;

  /** Whether the repository has issues enabled. */
  private Boolean hasIssues;

  /** Whether the repository has projects enabled. */
  private Boolean hasProjects;

  /** Whether the repository has a wiki enabled. */
  private Boolean hasWiki;

  /** Whether the repository has GitHub Pages enabled. */
  private Boolean hasPages;

  /** Whether the repository has discussions enabled. */
  private Boolean hasDiscussions;

  /** Whether the repository has downloads enabled. */
  private Boolean hasDownloads;

  /** Whether the repository was created from a template. */
  private Boolean isTemplate;

  /** Whether the repository is archived and read-only. */
  private Boolean archived;

  /** Whether the repository is disabled on GitHub. */
  private Boolean disabled;

  /** Visibility of the repository, e.g. "public"/"private". */
  private String visibility;

  /** Timestamp when the repository was last pushed to. */
  private Instant pushedAt;

  /** Timestamp when the repository was created. */
  private Instant createdAt;

  /** Timestamp when the repository was last updated. */
  private Instant updatedAt;

  /** Permissions the authenticated user has on this repo. */
  private Permissions permissions;

  /** Whether squash merging is allowed for pull requests. */
  private Boolean allowSquashMerge;

  /** Whether merge commits are allowed for pull requests. */
  private Boolean allowMergeCommit;

  /** Whether rebase merging is allowed for pull requests. */
  private Boolean allowRebaseMerge;

  /** Whether auto-merge is allowed for pull requests. */
  private Boolean allowAutoMerge;

  /** Whether head branches are deleted after merging. */
  private Boolean deleteBranchOnMerge;

  /** Whether pull request head branches can be updated. */
  private Boolean allowUpdateBranch;

  /** Default title format used for squash merge commits. */
  private String squashMergeCommitTitle;

  /** Default message format used for squash merge commits. */
  private String squashMergeCommitMessage;

  /** Default title format used for merge commits. */
  private String mergeCommitTitle;

  /** Default message format used for merge commits. */
  private String mergeCommitMessage;

  /** License under which the repository is distributed. */
  private License license;

  /** Whether forking the repository is allowed. */
  private Boolean allowForking;

  /** Whether web-based commit signoff is required. */
  private Boolean webCommitSignoffRequired;

  /** List of topic tags associated with the repository. */
  private List<String> topics;

  /** Temporary token allowing a one-time repository clone. */
  private String tempCloneToken;

  /** Permission levels the authenticated user has on a repo. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static final class Permissions {

    /** Whether the user has admin access to the repository. */
    private Boolean admin;

    /** Whether the user has maintain access to the repository. */
    private Boolean maintain;

    /** Whether the user has push access to the repository. */
    private Boolean push;

    /** Whether the user has triage access to the repository. */
    private Boolean triage;

    /** Whether the user has pull (read) access to the repo. */
    private Boolean pull;
  }

  /** License information for a repository. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static final class License {

    /** SPDX-style short identifier key of the license. */
    private String key;

    /** Human-readable name of the license. */
    private String name;

    /** API URL for the license resource. */
    private String url;

    /** SPDX license identifier. */
    private String spdxId;

    /** Global node ID of the license (GraphQL). */
    private String nodeId;

    /** URL of the license's GitHub web page. */
    private String htmlUrl;
  }
}
