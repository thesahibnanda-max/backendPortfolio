package net.sahibnanda.portfolio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import net.sahibnanda.portfolio.jackson.LeetcodeUserProfileResponseSubmissionCalendarDeserializer;

@Builder
@Jacksonized
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class LeetcodeUserProfileResponse {

  /** The data payload returned by the Leetcode GraphQL API. */
  private DataNode data;

  /** List of errors returned by the Leetcode GraphQL API, if any. */
  private List<Map<String, Object>> errors;

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class DataNode {

    /** The matched Leetcode user's profile data. */
    private MatchedUser matchedUser;

    /** The user's current contest ranking summary. */
    private UserContestRanking userContestRanking;

    /** The user's historical contest ranking records. */
    private List<UserContestRankingHistory> userContestRankingHistory;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class MatchedUser {

    /** The Leetcode username. */
    private String username;

    /** URL to the user's GitHub profile. */
    private String githubUrl;

    /** URL to the user's Twitter profile. */
    private String twitterUrl;

    /** URL to the user's LinkedIn profile. */
    private String linkedinUrl;

    /** The user's public profile details. */
    private Profile profile;

    /** The user's global submission statistics. */
    private SubmitStatsGlobal submitStatsGlobal;

    /** List of badges earned by the user. */
    private List<Badge> badges;

    /** The badge currently active/displayed for the user. */
    private Badge activeBadge;

    /** List of problems solved grouped by programming language. */
    private List<LanguageProblemCount> languageProblemCount;

    /** Counts of problems solved grouped by topic tag difficulty. */
    private TagProblemCounts tagProblemCounts;

    /** The user's submission calendar/activity data. */
    private UserCalendar userCalendar;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class Profile {

    /** The user's real name. */
    private String realName;

    /** URL to the user's avatar image. */
    private String userAvatar;

    /** The user's self-written biography. */
    private String aboutMe;

    /** The school associated with the user's profile. */
    private String school;

    /** List of personal websites linked on the user's profile. */
    private List<String> websites;

    /** The country associated with the user's profile. */
    private String countryName;

    /** The company associated with the user's profile. */
    private String company;

    /** The user's job title. */
    private String jobTitle;

    /** List of skill tags associated with the user. */
    private List<String> skillTags;

    /** The user's global Leetcode ranking. */
    private Integer ranking;

    /** The user's reputation score. */
    private Integer reputation;

    /** The user's star rating. */
    private Integer starRating;

    /** Number of views on the user's discussion posts. */
    private Integer postViewCount;

    /** Change in post view count since the last snapshot. */
    private Integer postViewCountDiff;

    /** Number of solutions posted by the user. */
    private Integer solutionCount;

    /** Change in solution count since the last snapshot. */
    private Integer solutionCountDiff;

    /** Number of category discussion posts made by the user. */
    private Integer categoryDiscussCount;

    /** Change in category discuss count since the last snapshot. */
    private Integer categoryDiscussCountDiff;

    /** The user's certification level. */
    private String certificationLevel;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class SubmitStatsGlobal {

    /** List of accepted submission counts grouped by difficulty. */
    private List<AcSubmissionNum> acSubmissionNum;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class AcSubmissionNum {

    /** The problem difficulty level (e.g. Easy, Medium, Hard). */
    private String difficulty;

    /** Number of accepted submissions for this difficulty. */
    private Integer count;

    /** Total number of submissions for this difficulty. */
    private Integer submissions;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class Badge {

    /** The badge's unique identifier. */
    private String id;

    /** The badge's display name. */
    private String displayName;

    /** URL to the badge's icon image. */
    private String icon;

    /** The date the badge was earned. */
    private String creationDate;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class LanguageProblemCount {

    /** The programming language name. */
    private String languageName;

    /** Number of problems solved using this language. */
    private Integer problemsSolved;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class TagProblemCounts {

    /** Problems solved counts for advanced-difficulty topic tags. */
    private List<TagProblemCount> advanced;

    /** Problems solved counts for intermediate-difficulty tags. */
    private List<TagProblemCount> intermediate;

    /** Problems solved counts for fundamental-difficulty tags. */
    private List<TagProblemCount> fundamental;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class TagProblemCount {

    /** The topic tag name. */
    private String tagName;

    /** Number of problems solved for this tag. */
    private Integer problemsSolved;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class UserCalendar {

    /** List of years the user was active on Leetcode. */
    private List<Integer> activeYears;

    /** The user's current submission streak, in days. */
    private Integer streak;

    /** Total number of days the user has been active. */
    private Integer totalActiveDays;

    /** Map of submission timestamps to daily submission counts. */
    @JsonDeserialize(
        using = LeetcodeUserProfileResponseSubmissionCalendarDeserializer.class)
    private Map<Long, Integer> submissionCalendar;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class UserContestRanking {

    /** Number of contests the user has attended. */
    private Integer attendedContestsCount;

    /** The user's current contest rating. */
    private Double rating;

    /** The user's global contest ranking. */
    private Integer globalRanking;

    /** Total number of contest participants. */
    private Integer totalParticipants;

    /** The user's percentile ranking among contest participants. */
    private Double topPercentage;

    /** The contest badge earned by the user. */
    private ContestBadge badge;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class ContestBadge {

    /** The contest badge name. */
    private String name;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class UserContestRankingHistory {

    /** Whether the user attended this contest. */
    private Boolean attended;

    /** The direction of the user's rating trend after this contest. */
    private String trendDirection;

    /** Number of problems solved in this contest. */
    private Integer problemsSolved;

    /** Total number of problems in this contest. */
    private Integer totalProblems;

    /** Time taken to finish the contest, in seconds. */
    private Integer finishTimeInSeconds;

    /** The user's contest rating after this contest. */
    private Double rating;

    /** The user's ranking in this contest. */
    private Integer ranking;

    /** The contest metadata. */
    private Contest contest;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class Contest {

    /** The contest's title. */
    private String title;

    /** The contest's start time, as a Unix timestamp. */
    private Long startTime;
  }
}
