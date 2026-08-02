package net.sahibnanda.portfolio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class GitHubCommit {

  private String url;
  private String sha;
  private String nodeId;
  private String htmlUrl;
  private String commentsUrl;

  private CommitDetail commit;

  private GitHubUser author;
  private GitHubUser committer;

  private List<Parent> parents;

  private Stats stats;
  private List<DiffEntry> files;

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class CommitDetail {
    private String url;
    private String message;
    private Integer commentCount;
    private GitAuthor author;
    private GitAuthor committer;
    private Tree tree;
    private Verification verification;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class GitAuthor {
    private String name;
    private String email;
    private Instant date;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Tree {
    private String sha;
    private String url;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Verification {
    private Boolean verified;
    private String reason;
    private String payload;
    private String signature;
    private String verifiedAt;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Parent {
    private String sha;
    private String url;
    private String htmlUrl;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Stats {
    private Integer additions;
    private Integer deletions;
    private Integer total;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class DiffEntry {
    private String sha;
    private String filename;
    private String status;
    private Integer additions;
    private Integer deletions;
    private Integer changes;
    private String blobUrl;
    private String rawUrl;
    private String contentsUrl;
    private String patch;
    private String previousFilename;
  }
}
