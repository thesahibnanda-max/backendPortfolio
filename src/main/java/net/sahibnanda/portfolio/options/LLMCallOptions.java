package net.sahibnanda.portfolio.options;

import lombok.Builder;
import lombok.Data;

/**
 * Per-call parameters for
 * {@link net.sahibnanda.portfolio.services.LLMService#call(LLMCallOptions)}.
 * {@code temperature}/{@code topP} left {@code null} are randomized within the
 * configured range instead of being explicitly supplied.
 */
@Builder
@Data
public final class LLMCallOptions {

  /** The system prompt to send. */
  private final String systemPrompt;

  /** The user prompt to send. */
  private final String userPrompt;

  /** Sampling temperature, or {@code null} to randomize it. */
  private final Double temperature;

  /** Nucleus sampling (top-p) value, or {@code null} to randomize it. */
  private final Double topP;
}
