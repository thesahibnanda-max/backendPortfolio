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

  private String status;
  private String comment;
  private List<RatingChange> result;

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  public static class RatingChange {

    private Integer contestId;
    private String contestName;
    private String handle;
    private Integer rank;
    private Long ratingUpdateTimeSeconds;
    private Integer oldRating;
    private Integer newRating;
  }
}
