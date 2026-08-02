package net.sahibnanda.portfolio.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.github.k0kubun.builder.query.graphql.GraphQLQueryBuilder;
import com.github.k0kubun.builder.query.graphql.builder.ObjectBuilder;
import com.github.k0kubun.builder.query.graphql.builder.QueryBuilder;
import com.github.k0kubun.builder.query.graphql.model.GraphQLObject;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
@Data
public final class LeetcodeUserProfileRequest {

  /** The GraphQL operation name sent to the Leetcode API. */
  private String operationName;

  /** The raw GraphQL query string sent to the Leetcode API. */
  private String query;

  /** The GraphQL query variables, e.g. the target username. */
  private Variables variables;

  @Builder
  @Data
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  private static class Variables {

    /** The Leetcode username to query. */
    private String username;
  }

  /**
   * Returns the username from the query variables, if set.
   *
   * @return the username, or {@code null} if no variables are set
   */
  public String getUsername() {
    return variables != null ? variables.getUsername() : null;
  }

  /**
   * Creates a new builder for the given Leetcode username.
   *
   * @param username the username to build the request for
   * @return a new builder targeting the given username
   */
  public static LeetcodeUserProfileRequestBuilder builder(
      final String username) {
    return new LeetcodeUserProfileRequestBuilder(username);
  }

  public static final class LeetcodeUserProfileRequestBuilder {

    /** GraphQL field/argument name for the username. */
    private static final String FIELD_USERNAME = "username";

    /** GraphQL field name for an identifier. */
    private static final String FIELD_ID = "id";

    /** GraphQL field name for a display name. */
    private static final String FIELD_DISPLAY_NAME = "displayName";

    /** GraphQL field name for an icon URL. */
    private static final String FIELD_ICON = "icon";

    /** GraphQL field name for a ranking value. */
    private static final String FIELD_RANKING = "ranking";

    /** GraphQL field name for a rating value. */
    private static final String FIELD_RATING = "rating";

    /** GraphQL field name for a topic tag name. */
    private static final String FIELD_TAG_NAME = "tagName";

    /** GraphQL field name for a problems-solved count. */
    private static final String FIELD_PROBLEMS_SOLVED = "problemsSolved";

    /** The Leetcode username this request targets. */
    private final String username;

    /** Whether to include the matchedUser section. */
    private boolean matchedUser;

    /** Whether to include the user's profile fields. */
    private boolean profile;

    /** Whether to include global submission statistics. */
    private boolean submitStats;

    /** Whether to include the user's badges. */
    private boolean badges;

    /** Whether to include the user's active badge. */
    private boolean activeBadge;

    /** Whether to include per-language problem counts. */
    private boolean languageProblemCount;

    /** Whether to include per-tag problem counts. */
    private boolean tagProblemCounts;

    /** Whether to include the user's submission calendar. */
    private boolean userCalendar;

    /** Whether to include the user's contest ranking. */
    private boolean contestRanking;

    /** Whether to include the user's contest ranking history. */
    private boolean contestHistory;

    /**
     * Creates a builder targeting the given Leetcode username.
     *
     * @param usernameValue the username this builder will target
     */
    private LeetcodeUserProfileRequestBuilder(final String usernameValue) {
      this.username = usernameValue;
    }

    /**
     * Enables the matchedUser section in the query.
     *
     * @return this builder, for chaining
     */
    public LeetcodeUserProfileRequestBuilder matchedUser() {
      this.matchedUser = true;
      return this;
    }

    /**
     * Enables the user's profile fields in the query.
     *
     * @return this builder, for chaining
     */
    public LeetcodeUserProfileRequestBuilder profile() {
      this.profile = true;
      return this;
    }

    /**
     * Enables global submission statistics in the query.
     *
     * @return this builder, for chaining
     */
    public LeetcodeUserProfileRequestBuilder submissions() {
      this.submitStats = true;
      return this;
    }

    /**
     * Enables the user's badges in the query.
     *
     * @return this builder, for chaining
     */
    public LeetcodeUserProfileRequestBuilder badges() {
      this.badges = true;
      return this;
    }

    /**
     * Enables the user's active badge in the query.
     *
     * @return this builder, for chaining
     */
    public LeetcodeUserProfileRequestBuilder activeBadge() {
      this.activeBadge = true;
      return this;
    }

    /**
     * Enables per-language problem counts in the query.
     *
     * @return this builder, for chaining
     */
    public LeetcodeUserProfileRequestBuilder languageStats() {
      this.languageProblemCount = true;
      return this;
    }

    /**
     * Enables per-tag problem counts in the query.
     *
     * @return this builder, for chaining
     */
    public LeetcodeUserProfileRequestBuilder tagStats() {
      this.tagProblemCounts = true;
      return this;
    }

    /**
     * Enables the user's submission calendar in the query.
     *
     * @return this builder, for chaining
     */
    public LeetcodeUserProfileRequestBuilder calendar() {
      this.userCalendar = true;
      return this;
    }

    /**
     * Enables the user's contest ranking in the query.
     *
     * @return this builder, for chaining
     */
    public LeetcodeUserProfileRequestBuilder contestRanking() {
      this.contestRanking = true;
      return this;
    }

    /**
     * Enables the user's contest ranking history in the query.
     *
     * @return this builder, for chaining
     */
    public LeetcodeUserProfileRequestBuilder contestHistory() {
      this.contestHistory = true;
      return this;
    }

    /**
     * Enables every optional section supported by this builder.
     *
     * @return this builder, for chaining
     */
    public LeetcodeUserProfileRequestBuilder everything() {
      return matchedUser().profile().submissions().badges().activeBadge()
          .languageStats().tagStats().calendar().contestRanking()
          .contestHistory();
    }

    /**
     * Builds the {@link LeetcodeUserProfileRequest} by assembling a GraphQL
     * query from every section that was enabled on this builder.
     *
     * @return the constructed request, ready to be sent to Leetcode
     */
    public LeetcodeUserProfileRequest build() {

      final ObjectBuilder matchedUserBuilder = GraphQLQueryBuilder.object();

      addMatchedUserFields(matchedUserBuilder);
      addProfileField(matchedUserBuilder);
      addSubmitStatsField(matchedUserBuilder);
      addBadgesField(matchedUserBuilder);
      addActiveBadgeField(matchedUserBuilder);
      addLanguageProblemCountField(matchedUserBuilder);
      addTagProblemCountsField(matchedUserBuilder);
      addUserCalendarField(matchedUserBuilder);

      final QueryBuilder query = GraphQLQueryBuilder.query();

      final Map<String, Object> usernameArg = Map.of(FIELD_USERNAME, username);

      query.object("matchedUser", usernameArg, matchedUserBuilder.build());

      addContestRankingField(query, usernameArg);
      addContestHistoryField(query, usernameArg);

      final LeetcodeUserProfileRequest request =
          new LeetcodeUserProfileRequest();
      request.operationName = "userPublicProfile";
      request.query = "query userPublicProfile { " + query.build() + " }";
      request.variables = Variables.builder().username(username).build();

      return request;
    }

    /**
     * Adds the top-level matchedUser scalar fields, if enabled.
     *
     * @param builder the matchedUser object builder to mutate
     */
    private void addMatchedUserFields(final ObjectBuilder builder) {
      if (matchedUser) {
        builder.field(FIELD_USERNAME).field("githubUrl").field("twitterUrl")
            .field("linkedinUrl");
      }
    }

    /**
     * Adds the nested profile object, if enabled.
     *
     * @param builder the matchedUser object builder to mutate
     */
    private void addProfileField(final ObjectBuilder builder) {
      if (profile) {
        builder.object("profile", buildProfileObject());
      }
    }

    /**
     * Builds the GraphQL object describing the profile fields.
     *
     * @return the constructed profile field selection
     */
    private static GraphQLObject buildProfileObject() {
      return GraphQLQueryBuilder.object().field("realName").field("userAvatar")
          .field("aboutMe").field("school").field("websites")
          .field("countryName").field("company").field("jobTitle")
          .field("skillTags").field(FIELD_RANKING).field("reputation")
          .field("starRating").field("postViewCount").field("postViewCountDiff")
          .field("solutionCount").field("solutionCountDiff")
          .field("categoryDiscussCount").field("categoryDiscussCountDiff")
          .field("certificationLevel").build();
    }

    /**
     * Adds the nested submitStatsGlobal object, if enabled.
     *
     * @param builder the matchedUser object builder to mutate
     */
    private void addSubmitStatsField(final ObjectBuilder builder) {
      if (submitStats) {
        builder.object("submitStatsGlobal", buildSubmitStatsObject());
      }
    }

    /**
     * Builds the GraphQL object describing submission statistics.
     *
     * @return the constructed submission statistics selection
     */
    private static GraphQLObject buildSubmitStatsObject() {
      return GraphQLQueryBuilder.object()
          .object("acSubmissionNum", GraphQLQueryBuilder.object()
              .field("difficulty").field("count").field("submissions").build())
          .build();
    }

    /**
     * Adds the nested badges object, if enabled.
     *
     * @param builder the matchedUser object builder to mutate
     */
    private void addBadgesField(final ObjectBuilder builder) {
      if (badges) {
        builder.object("badges", buildBadgeObject());
      }
    }

    /**
     * Adds the nested activeBadge object, if enabled.
     *
     * @param builder the matchedUser object builder to mutate
     */
    private void addActiveBadgeField(final ObjectBuilder builder) {
      if (activeBadge) {
        builder.object("activeBadge", buildBadgeObject());
      }
    }

    /**
     * Builds the GraphQL object describing a badge's id, display name and icon.
     * Used for both badges and activeBadge.
     *
     * @return the constructed badge field selection
     */
    private static GraphQLObject buildBadgeObject() {
      return GraphQLQueryBuilder.object().field(FIELD_ID)
          .field(FIELD_DISPLAY_NAME).field(FIELD_ICON).build();
    }

    /**
     * Adds the nested languageProblemCount object, if enabled.
     *
     * @param builder the matchedUser object builder to mutate
     */
    private void addLanguageProblemCountField(final ObjectBuilder builder) {
      if (languageProblemCount) {
        builder.object("languageProblemCount", GraphQLQueryBuilder.object()
            .field("languageName").field(FIELD_PROBLEMS_SOLVED).build());
      }
    }

    /**
     * Adds the nested tagProblemCounts object, if enabled.
     *
     * @param builder the matchedUser object builder to mutate
     */
    private void addTagProblemCountsField(final ObjectBuilder builder) {
      if (tagProblemCounts) {
        builder.object("tagProblemCounts", buildTagProblemCountsObject());
      }
    }

    /**
     * Builds the GraphQL object describing problems-solved counts grouped by
     * advanced, intermediate and fundamental tags.
     *
     * @return the constructed tag problem counts selection
     */
    private static GraphQLObject buildTagProblemCountsObject() {
      return GraphQLQueryBuilder.object()
          .object("advanced", buildTagProblemCountObject())
          .object("intermediate", buildTagProblemCountObject())
          .object("fundamental", buildTagProblemCountObject()).build();
    }

    /**
     * Builds the GraphQL object describing a single tag's counts.
     *
     * @return the constructed tag problem count selection
     */
    private static GraphQLObject buildTagProblemCountObject() {
      return GraphQLQueryBuilder.object().field(FIELD_TAG_NAME)
          .field(FIELD_PROBLEMS_SOLVED).build();
    }

    /**
     * Adds the nested userCalendar object, if enabled.
     *
     * @param builder the matchedUser object builder to mutate
     */
    private void addUserCalendarField(final ObjectBuilder builder) {
      if (userCalendar) {
        builder.object("userCalendar",
            GraphQLQueryBuilder.object().field("activeYears").field("streak")
                .field("totalActiveDays").field("submissionCalendar").build());
      }
    }

    /**
     * Adds the top-level userContestRanking query, if enabled.
     *
     * @param query the top-level query builder to mutate
     * @param usernameArg the {@code username} argument map to attach
     */
    private void addContestRankingField(final QueryBuilder query,
        final Map<String, Object> usernameArg) {
      if (contestRanking) {
        query.object("userContestRanking", usernameArg,
            buildContestRankingObject());
      }
    }

    /**
     * Builds the GraphQL object describing contest ranking fields.
     *
     * @return the constructed contest ranking selection
     */
    private static GraphQLObject buildContestRankingObject() {
      return GraphQLQueryBuilder.object().field("attendedContestsCount")
          .field(FIELD_RATING).field("globalRanking").field("totalParticipants")
          .field("topPercentage")
          .object("badge", GraphQLQueryBuilder.object().field("name").build())
          .build();
    }

    /**
     * Adds the top-level userContestRankingHistory query, if enabled.
     *
     * @param query the top-level query builder to mutate
     * @param usernameArg the {@code username} argument map to attach
     */
    private void addContestHistoryField(final QueryBuilder query,
        final Map<String, Object> usernameArg) {
      if (contestHistory) {
        query.object("userContestRankingHistory", usernameArg,
            buildContestHistoryObject());
      }
    }

    /**
     * Builds the GraphQL object describing contest history fields.
     *
     * @return the constructed contest history selection
     */
    private static GraphQLObject buildContestHistoryObject() {
      return GraphQLQueryBuilder.object().field("attended")
          .field("trendDirection").field(FIELD_PROBLEMS_SOLVED)
          .field("totalProblems").field("finishTimeInSeconds")
          .field(FIELD_RATING).field(FIELD_RANKING)
          .object("contest", GraphQLQueryBuilder.object().field("title")
              .field("startTime").build())
          .build();
    }
  }
}
