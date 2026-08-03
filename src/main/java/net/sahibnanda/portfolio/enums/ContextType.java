package net.sahibnanda.portfolio.enums;

import lombok.Getter;

/**
 * Knowledge domains the Orchestrator AI can route a user's question to.
 */
@Getter
public enum ContextType {

  /**
   * Resume, experience, education, projects, technical skills and languages.
   */
  PROFILE("Resume, experience, education, projects, technical skills and "
      + "programming languages used"),

  /** GitHub repositories, technologies, commits. */
  GITHUB("GitHub repositories, technologies, commits"),

  /** Ratings, problems solved, contests. */
  LEETCODE("Ratings, problems solved, contests"),

  /** Competitive programming profile. */
  CODEFORCES("Competitive programming profile"),

  /** Non-technical personal preferences, hobbies, working style. */
  PERSONALITY("Non-technical personal preferences, hobbies, lifestyle, "
      + "and working style"),

  /** General question requiring no personal context. */
  NONE("General question requiring no personal context");

  /** Human-readable description of this domain, shown to the Orchestrator. */
  private final String description;

  ContextType(final String contextDescription) {
    this.description = contextDescription;
  }
}
