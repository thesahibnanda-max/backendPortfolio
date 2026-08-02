package net.sahibnanda.portfolio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

/** A single Git commit as returned by the GitHub REST API. */
@Builder
@Jacksonized
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class GitHubCommit {

  /** API URL for this commit resource. */
  private String url;

  /** SHA-1 hash identifying the commit. */
  private String sha;

  /** Global node ID of the commit (GraphQL). */
  private String nodeId;

  /** URL of the commit's GitHub web page. */
  private String htmlUrl;

  /** API URL for comments on this commit. */
  private String commentsUrl;

  /** Underlying Git commit details (message, tree, etc.). */
  private CommitDetail commit;

  /** GitHub account credited as the commit's author. */
  private GitHubUser author;

  /** GitHub account credited as the commit's committer. */
  private GitHubUser committer;

  /** Parent commits of this commit. */
  private List<Parent> parents;

  /** Line addition/deletion statistics for this commit. */
  private Stats stats;

  /** Files changed by this commit. */
  private List<DiffEntry> files;

  /** Raw Git commit metadata (message, authorship, tree). */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static final class CommitDetail {

    /** API URL for the underlying Git commit object. */
    private String url;

    /** Commit message text. */
    private String message;

    /** Number of comments on this commit. */
    private Integer commentCount;

    /** Git author identity and timestamp. */
    private GitAuthor author;

    /** Git committer identity and timestamp. */
    private GitAuthor committer;

    /** Git tree object associated with the commit. */
    private Tree tree;

    /** GPG/SSH signature verification status. */
    private Verification verification;
  }

  /** Raw Git author or committer identity and timestamp. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static final class GitAuthor {

    /** Name of the Git author or committer. */
    private String name;

    /** Email address of the Git author or committer. */
    private String email;

    /** Timestamp of the authorship or commit action. */
    private Instant date;
  }

  /** Reference to the Git tree object for a commit. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static final class Tree {

    /** SHA-1 hash of the Git tree object. */
    private String sha;

    /** API URL for the Git tree object. */
    private String url;
  }

  /** Signature verification result for a Git commit. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static final class Verification {

    /** Whether the commit signature was verified. */
    private Boolean verified;

    /** Reason code explaining the verification result. */
    private String reason;

    /** Signed payload used for verification. */
    private String payload;

    /** Signature bytes used for verification. */
    private String signature;

    /** Timestamp when the signature was verified. */
    private String verifiedAt;
  }

  /** Reference to a parent commit of a Git commit. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static final class Parent {

    /** SHA-1 hash of the parent commit. */
    private String sha;

    /** API URL for the parent commit. */
    private String url;

    /** URL of the parent commit's GitHub web page. */
    private String htmlUrl;
  }

  /** Aggregate line change statistics for a commit. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static final class Stats {

    /** Number of lines added by the commit. */
    private Integer additions;

    /** Number of lines deleted by the commit. */
    private Integer deletions;

    /** Total number of lines changed by the commit. */
    private Integer total;
  }

  /** Description of a single file changed by a commit. */
  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static final class DiffEntry {

    /** SHA-1 blob hash of the file's contents. */
    private String sha;

    /** Path of the file within the repository. */
    private String filename;

    /** Change status, e.g. "added", "modified", "removed". */
    private String status;

    /** Number of lines added to this file. */
    private Integer additions;

    /** Number of lines deleted from this file. */
    private Integer deletions;

    /** Total number of lines changed in this file. */
    private Integer changes;

    /** API URL for the file's Git blob. */
    private String blobUrl;

    /** URL to fetch the file's raw contents. */
    private String rawUrl;

    /** API URL for the file's contents endpoint. */
    private String contentsUrl;

    /** Unified diff patch text for this file. */
    private String patch;

    /** Previous filename, present when the file was renamed. */
    private String previousFilename;
  }
}
