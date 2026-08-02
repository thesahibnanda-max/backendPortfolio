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

  private DataNode data;
  private List<Map<String, Object>> errors;

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class DataNode {

    private MatchedUser matchedUser;
    private UserContestRanking userContestRanking;
    private List<UserContestRankingHistory> userContestRankingHistory;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class MatchedUser {

    private String username;
    private String githubUrl;
    private String twitterUrl;
    private String linkedinUrl;

    private Profile profile;

    private SubmitStatsGlobal submitStatsGlobal;

    private List<Badge> badges;

    private Badge activeBadge;

    private List<LanguageProblemCount> languageProblemCount;

    private TagProblemCounts tagProblemCounts;

    private UserCalendar userCalendar;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class Profile {

    private String realName;
    private String userAvatar;
    private String aboutMe;
    private String school;

    private List<String> websites;

    private String countryName;
    private String company;
    private String jobTitle;

    private List<String> skillTags;

    private Integer ranking;
    private Integer reputation;
    private Integer starRating;

    private Integer postViewCount;
    private Integer postViewCountDiff;

    private Integer solutionCount;
    private Integer solutionCountDiff;

    private Integer categoryDiscussCount;
    private Integer categoryDiscussCountDiff;

    private String certificationLevel;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class SubmitStatsGlobal {

    private List<AcSubmissionNum> acSubmissionNum;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class AcSubmissionNum {

    private String difficulty;
    private Integer count;
    private Integer submissions;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class Badge {

    private String id;
    private String displayName;
    private String icon;
    private String creationDate;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class LanguageProblemCount {

    private String languageName;
    private Integer problemsSolved;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class TagProblemCounts {

    private List<TagProblemCount> advanced;
    private List<TagProblemCount> intermediate;
    private List<TagProblemCount> fundamental;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class TagProblemCount {

    private String tagName;
    private Integer problemsSolved;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class UserCalendar {

    private List<Integer> activeYears;

    private Integer streak;
    private Integer totalActiveDays;

    @JsonDeserialize(using = LeetcodeUserProfileResponseSubmissionCalendarDeserializer.class)
    private Map<Long, Integer> submissionCalendar;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class UserContestRanking {

    private Integer attendedContestsCount;
    private Double rating;
    private Integer globalRanking;
    private Integer totalParticipants;
    private Double topPercentage;

    private ContestBadge badge;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class ContestBadge {

    private String name;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class UserContestRankingHistory {

    private Boolean attended;
    private String trendDirection;

    private Integer problemsSolved;
    private Integer totalProblems;

    private Integer finishTimeInSeconds;

    private Double rating;

    private Integer ranking;

    private Contest contest;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class Contest {

    private String title;
    private Long startTime;
  }
}
