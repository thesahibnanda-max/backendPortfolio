package net.sahibnanda.portfolio.objects;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Codeforces-only competitive-programming stats for a single user, derived from
 * their contest rating history (the only data the Codeforces client currently
 * exposes).
 */
@Builder
@Data
public final class CodeforcesDetails {

  /** The Codeforces handle. */
  private final String handle;

  /** The user's rating after their most recent rated contest. */
  private final Integer currentRating;

  /** The highest rating the user has ever reached. */
  private final Integer maxRating;

  /** Total number of rated contests the user has participated in. */
  private final Integer contestsCount;

  /** The user's rating transitions, one per rated contest, in order. */
  private final List<RatingTransition> ratingHistory;

  /** A single contest's effect on the user's rating. */
  @Builder
  @Data
  public static final class RatingTransition {

    /** The contest's name. */
    private final String contestName;

    /** The user's placement in the contest. */
    private final Integer rank;

    /** The user's rating before the contest. */
    private final Integer oldRating;

    /** The user's rating after the contest. */
    private final Integer newRating;

    /** When the rating was updated. */
    private final Instant contestTime;
  }
}
