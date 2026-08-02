package net.sahibnanda.portfolio.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.github.k0kubun.builder.query.graphql.GraphQLQueryBuilder;
import com.github.k0kubun.builder.query.graphql.builder.ObjectBuilder;
import com.github.k0kubun.builder.query.graphql.builder.QueryBuilder;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
@Data
public class LeetcodeUserProfileRequest {

  private String operationName;
  private String query;
  private Variables variables;

  @Builder
  @Data
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  private static class Variables {
    private String username;
  }

  public String getUsername() {
    return variables != null ? variables.getUsername() : null;
  }

  public static LeetcodeUserProfileRequestBuilder builder(String username) {
    return new LeetcodeUserProfileRequestBuilder(username);
  }

  public static final class LeetcodeUserProfileRequestBuilder {

    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_ID = "id";
    private static final String FIELD_DISPLAY_NAME = "displayName";
    private static final String FIELD_ICON = "icon";
    private static final String FIELD_RANKING = "ranking";
    private static final String FIELD_RATING = "rating";
    private static final String FIELD_TAG_NAME = "tagName";
    private static final String FIELD_PROBLEMS_SOLVED = "problemsSolved";

    private final String username;

    private boolean matchedUser;
    private boolean profile;
    private boolean submitStats;
    private boolean badges;
    private boolean activeBadge;
    private boolean languageProblemCount;
    private boolean tagProblemCounts;
    private boolean userCalendar;
    private boolean contestRanking;
    private boolean contestHistory;

    private LeetcodeUserProfileRequestBuilder(String username) {
      this.username = username;
    }

    public LeetcodeUserProfileRequestBuilder matchedUser() {
      this.matchedUser = true;
      return this;
    }

    public LeetcodeUserProfileRequestBuilder profile() {
      this.profile = true;
      return this;
    }

    public LeetcodeUserProfileRequestBuilder submissions() {
      this.submitStats = true;
      return this;
    }

    public LeetcodeUserProfileRequestBuilder badges() {
      this.badges = true;
      return this;
    }

    public LeetcodeUserProfileRequestBuilder activeBadge() {
      this.activeBadge = true;
      return this;
    }

    public LeetcodeUserProfileRequestBuilder languageStats() {
      this.languageProblemCount = true;
      return this;
    }

    public LeetcodeUserProfileRequestBuilder tagStats() {
      this.tagProblemCounts = true;
      return this;
    }

    public LeetcodeUserProfileRequestBuilder calendar() {
      this.userCalendar = true;
      return this;
    }

    public LeetcodeUserProfileRequestBuilder contestRanking() {
      this.contestRanking = true;
      return this;
    }

    public LeetcodeUserProfileRequestBuilder contestHistory() {
      this.contestHistory = true;
      return this;
    }

    public LeetcodeUserProfileRequestBuilder everything() {
      return matchedUser()
          .profile()
          .submissions()
          .badges()
          .activeBadge()
          .languageStats()
          .tagStats()
          .calendar()
          .contestRanking()
          .contestHistory();
    }

    public LeetcodeUserProfileRequest build() {

      ObjectBuilder matchedUserBuilder = GraphQLQueryBuilder.object();

      if (matchedUser) {
        matchedUserBuilder
            .field(FIELD_USERNAME)
            .field("githubUrl")
            .field("twitterUrl")
            .field("linkedinUrl");
      }

      if (profile) {
        matchedUserBuilder.object(
            "profile",
            GraphQLQueryBuilder.object()
                .field("realName")
                .field("userAvatar")
                .field("aboutMe")
                .field("school")
                .field("websites")
                .field("countryName")
                .field("company")
                .field("jobTitle")
                .field("skillTags")
                .field(FIELD_RANKING)
                .field("reputation")
                .field("starRating")
                .field("postViewCount")
                .field("postViewCountDiff")
                .field("solutionCount")
                .field("solutionCountDiff")
                .field("categoryDiscussCount")
                .field("categoryDiscussCountDiff")
                .field("certificationLevel")
                .build());
      }

      if (submitStats) {
        matchedUserBuilder.object(
            "submitStatsGlobal",
            GraphQLQueryBuilder.object()
                .object(
                    "acSubmissionNum",
                    GraphQLQueryBuilder.object()
                        .field("difficulty")
                        .field("count")
                        .field("submissions")
                        .build())
                .build());
      }

      if (badges) {
        matchedUserBuilder.object(
            "badges",
            GraphQLQueryBuilder.object()
                .field(FIELD_ID)
                .field(FIELD_DISPLAY_NAME)
                .field(FIELD_ICON)
                .field("creationDate")
                .build());
      }

      if (activeBadge) {
        matchedUserBuilder.object(
            "activeBadge",
            GraphQLQueryBuilder.object()
                .field(FIELD_ID)
                .field(FIELD_DISPLAY_NAME)
                .field(FIELD_ICON)
                .build());
      }

      if (languageProblemCount) {
        matchedUserBuilder.object(
            "languageProblemCount",
            GraphQLQueryBuilder.object()
                .field("languageName")
                .field(FIELD_PROBLEMS_SOLVED)
                .build());
      }

      if (tagProblemCounts) {
        matchedUserBuilder.object(
            "tagProblemCounts",
            GraphQLQueryBuilder.object()
                .object(
                    "advanced",
                    GraphQLQueryBuilder.object()
                        .field(FIELD_TAG_NAME)
                        .field(FIELD_PROBLEMS_SOLVED)
                        .build())
                .object(
                    "intermediate",
                    GraphQLQueryBuilder.object()
                        .field(FIELD_TAG_NAME)
                        .field(FIELD_PROBLEMS_SOLVED)
                        .build())
                .object(
                    "fundamental",
                    GraphQLQueryBuilder.object()
                        .field(FIELD_TAG_NAME)
                        .field(FIELD_PROBLEMS_SOLVED)
                        .build())
                .build());
      }

      if (userCalendar) {
        matchedUserBuilder.object(
            "userCalendar",
            GraphQLQueryBuilder.object()
                .field("activeYears")
                .field("streak")
                .field("totalActiveDays")
                .field("submissionCalendar")
                .build());
      }

      QueryBuilder query = GraphQLQueryBuilder.query();

      Map<String, Object> usernameArg = Map.of(FIELD_USERNAME, username);

      query.object("matchedUser", usernameArg, matchedUserBuilder.build());

      if (contestRanking) {
        query.object(
            "userContestRanking",
            usernameArg,
            GraphQLQueryBuilder.object()
                .field("attendedContestsCount")
                .field(FIELD_RATING)
                .field("globalRanking")
                .field("totalParticipants")
                .field("topPercentage")
                .object("badge", GraphQLQueryBuilder.object().field("name").build())
                .build());
      }

      if (contestHistory) {
        query.object(
            "userContestRankingHistory",
            usernameArg,
            GraphQLQueryBuilder.object()
                .field("attended")
                .field("trendDirection")
                .field(FIELD_PROBLEMS_SOLVED)
                .field("totalProblems")
                .field("finishTimeInSeconds")
                .field(FIELD_RATING)
                .field(FIELD_RANKING)
                .object(
                    "contest",
                    GraphQLQueryBuilder.object().field("title").field("startTime").build())
                .build());
      }

      LeetcodeUserProfileRequest request = new LeetcodeUserProfileRequest();
      request.operationName = "userPublicProfile";
      request.query = "query userPublicProfile { " + query.build() + " }";
      request.variables = Variables.builder().username(username).build();

      return request;
    }
  }
}
