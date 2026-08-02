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

@Builder
@Jacksonized
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GitHubRepository {

  private Long id;
  private String nodeId;
  private String name;
  private String fullName;
  private GitHubUser owner;

  @JsonProperty("private")
  private Boolean isPrivate;

  private String htmlUrl;
  private String description;
  private Boolean fork;
  private String url;

  private String archiveUrl;
  private String assigneesUrl;
  private String blobsUrl;
  private String branchesUrl;
  private String collaboratorsUrl;
  private String commentsUrl;
  private String commitsUrl;
  private String compareUrl;
  private String contentsUrl;
  private String contributorsUrl;
  private String deploymentsUrl;
  private String downloadsUrl;
  private String eventsUrl;
  private String forksUrl;
  private String gitCommitsUrl;
  private String gitRefsUrl;
  private String gitTagsUrl;
  private String issueCommentUrl;
  private String issueEventsUrl;
  private String issuesUrl;
  private String keysUrl;
  private String labelsUrl;
  private String languagesUrl;
  private String mergesUrl;
  private String milestonesUrl;
  private String notificationsUrl;
  private String pullsUrl;
  private String releasesUrl;
  private String stargazersUrl;
  private String statusesUrl;
  private String subscribersUrl;
  private String subscriptionUrl;
  private String tagsUrl;
  private String teamsUrl;
  private String treesUrl;
  private String hooksUrl;

  private String gitUrl;
  private String sshUrl;
  private String cloneUrl;
  private String svnUrl;
  private String mirrorUrl;

  private String homepage;
  private String language;
  private Integer forksCount;
  private Integer stargazersCount;
  private Integer watchersCount;
  private Integer size;
  private String defaultBranch;
  private Integer openIssuesCount;
  private Integer forks;
  private Integer openIssues;
  private Integer watchers;

  private Boolean hasIssues;
  private Boolean hasProjects;
  private Boolean hasWiki;
  private Boolean hasPages;
  private Boolean hasDiscussions;
  private Boolean hasDownloads;
  private Boolean isTemplate;
  private Boolean archived;
  private Boolean disabled;

  private String visibility;
  private Instant pushedAt;
  private Instant createdAt;
  private Instant updatedAt;

  private Permissions permissions;

  private Boolean allowSquashMerge;
  private Boolean allowMergeCommit;
  private Boolean allowRebaseMerge;
  private Boolean allowAutoMerge;
  private Boolean deleteBranchOnMerge;
  private Boolean allowUpdateBranch;
  private String squashMergeCommitTitle;
  private String squashMergeCommitMessage;
  private String mergeCommitTitle;
  private String mergeCommitMessage;

  private License license;

  private Boolean allowForking;
  private Boolean webCommitSignoffRequired;
  private List<String> topics;
  private String tempCloneToken;

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Permissions {
    private Boolean admin;
    private Boolean maintain;
    private Boolean push;
    private Boolean triage;
    private Boolean pull;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class License {
    private String key;
    private String name;
    private String url;
    private String spdxId;
    private String nodeId;
    private String htmlUrl;
  }
}
