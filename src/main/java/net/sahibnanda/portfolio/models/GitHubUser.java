package net.sahibnanda.portfolio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GitHubUser {

  private String login;
  private Long id;
  private String nodeId;
  private String avatarUrl;
  private String gravatarId;
  private String url;
  private String htmlUrl;
  private String followersUrl;
  private String followingUrl;
  private String gistsUrl;
  private String starredUrl;
  private String subscriptionsUrl;
  private String organizationsUrl;
  private String reposUrl;
  private String eventsUrl;
  private String receivedEventsUrl;
  private String type;
  private String userViewType;
  private Boolean siteAdmin;

  private String name;
  private String company;
  private String blog;
  private String location;
  private String email;
  private String notificationEmail;
  private Boolean hireable;
  private String bio;
  private String twitterUsername;

  private Integer publicRepos;
  private Integer publicGists;
  private Integer followers;
  private Integer following;

  private Instant createdAt;
  private Instant updatedAt;

  private Plan plan;
  private String starredAt;
  private String ldapDn;
  private Boolean businessPlus;

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Plan {
    private Integer collaborators;
    private String name;
    private Integer space;
    private Integer privateRepos;
  }
}
