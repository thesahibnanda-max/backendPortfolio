package net.sahibnanda.portfolio.objects;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * LeetCode-only competitive-programming stats for a single user. Holds no
 * identity/personal fields — those belong to {@link ProfileDetails}/
 * {@link PersonalityDetails} instead, even though LeetCode's raw API response
 * happens to include some of them too.
 */
@Builder
@Data
public final class LeetcodeDetails {

  /** The LeetCode username. */
  private final String username;

  /** The user's global LeetCode ranking. */
  private final Integer ranking;

  /** The user's reputation score. */
  private final Integer reputation;

  /** Total number of problems solved, across all difficulties. */
  private final Integer totalSolved;

  /** Number of easy-difficulty problems solved. */
  private final Integer easySolved;

  /** Number of medium-difficulty problems solved. */
  private final Integer mediumSolved;

  /** Number of hard-difficulty problems solved. */
  private final Integer hardSolved;

  /** Display names of badges earned. */
  private final List<String> badges;

  /** Problems solved per programming language. */
  private final Map<String, Integer> languageProblemsSolved;

  /** Problems solved per advanced-difficulty topic tag, if available. */
  private final Map<String, Integer> advancedTagsSolved;

  /** Problems solved per intermediate-difficulty topic tag, if available. */
  private final Map<String, Integer> intermediateTagsSolved;

  /** Problems solved per fundamental-difficulty topic tag, if available. */
  private final Map<String, Integer> fundamentalTagsSolved;

  /** The user's current contest rating. */
  private final Double contestRating;

  /** The user's global contest ranking. */
  private final Integer contestGlobalRanking;

  /** The user's current submission streak, in days. */
  private final Integer currentStreak;

  /** Total number of days the user has been active. */
  private final Integer totalActiveDays;
}
