package net.sahibnanda.portfolio.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class CodeforcesUserRatingResponse {

  /** Status of the Codeforces API response, e.g. "OK". */
  private String status;
  /** Error comment returned when the status is not "OK". */
  private String comment;
  /** List of rating changes for the requested user. */
  private List<RatingChange> result;

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class RatingChange {

    /** Identifier of the contest that caused this change. */
    private Integer contestId;
    /** Name of the contest that caused this change. */
    private String contestName;
    /** Codeforces handle of the user. */
    private String handle;
    /** User's placement in the contest. */
    private Integer rank;
    /** Unix timestamp of when the rating was updated. */
    private Long ratingUpdateTimeSeconds;
    /** User's rating before the contest. */
    private Integer oldRating;
    /** User's rating after the contest. */
    private Integer newRating;
  }
}
