package net.sahibnanda.portfolio.enums;

import lombok.Getter;

/** Groq's {@code reasoning_effort} request parameter values. */
@Getter
public enum ReasoningEffort {

  /** The model's default reasoning effort. */
  DEFAULT("default"),

  /** Medium reasoning effort. */
  MEDIUM("medium"),

  /** Reasoning disabled entirely. */
  NONE("none");

  /** The literal value Groq expects for this effort level. */
  private final String value;

  ReasoningEffort(final String reasoningEffortValue) {
    this.value = reasoningEffortValue;
  }

}
