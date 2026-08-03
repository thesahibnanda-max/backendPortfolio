package net.sahibnanda.portfolio.enums;

import lombok.Getter;

/**
 * Knowledge domains the Orchestrator AI can route a user's question to.
 */
@Getter
public enum ContextType {

  /**
   * Resume: experience, education, projects, technical skills, achievements,
   * contact links, spoken languages.
   */
  PROFILE("Resume: experience, education, projects, technical skills, "
      + "achievements, contact links, spoken languages"),

  /** GitHub profile: repositories, languages used, stars, followers, bio. */
  GITHUB("GitHub profile: repositories, languages used, stars, followers, "
      + "bio"),

  /**
   * LeetCode competitive programming profile: rank, problems solved, contest
   * rating, streak, languages used.
   */
  LEETCODE("LeetCode competitive programming profile: rank, problems "
      + "solved, contest rating, streak, languages used"),

  /**
   * Codeforces competitive programming profile: rating, contest history.
   */
  CODEFORCES("Codeforces competitive programming profile: rating, contest "
      + "history"),

  /**
   * Non-technical personal profile: interests, hobbies, favorites (movies,
   * games, sports), personality traits, appearance, spoken languages, working
   * style.
   */
  PERSONALITY("Non-technical personal profile: interests, hobbies, "
      + "favorites (movies, games, sports), personality traits, "
      + "appearance, spoken languages, working style"),

  /** General question requiring no personal context. */
  NONE("General question requiring no personal context");

  /** Human-readable description of this domain, shown to the Orchestrator. */
  private final String description;

  ContextType(final String contextDescription) {
    this.description = contextDescription;
  }
}
