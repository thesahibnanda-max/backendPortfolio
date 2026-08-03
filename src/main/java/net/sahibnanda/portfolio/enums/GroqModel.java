package net.sahibnanda.portfolio.enums;

import java.util.Optional;
import lombok.Getter;

/**
 * Groq chat-completion models this application selects between at random,
 * weighted by {@code llm.model-weights} in application.yml.
 */
public enum GroqModel {

  /** Llama 3.3 70B (versatile). */
  LLAMA_3_3_70B("llama-3.3-70b-versatile", null),

  /** GPT-OSS 20B. */
  GPT_OSS_20B("openai/gpt-oss-20b", ReasoningEffort.MEDIUM),

  /** Qwen 3.6 27B. */
  QWEN_3_6_27B("qwen/qwen3.6-27b", ReasoningEffort.NONE),

  /** GPT-OSS 120B. */
  GPT_OSS_120B("openai/gpt-oss-120b", ReasoningEffort.MEDIUM);

  /** The literal model id Groq expects in a chat-completion request. */
  @Getter
  private final String modelId;

  /** This model's reasoning effort, or empty if it doesn't support one. */
  private final ReasoningEffort reasoningEffort;

  GroqModel(final String groqModelId,
      final ReasoningEffort modelReasoningEffort) {
    this.modelId = groqModelId;
    this.reasoningEffort = modelReasoningEffort;
  }

  /**
   * Returns this model's reasoning effort, if it supports one.
   *
   * @return the reasoning effort, or empty if this model doesn't support one
   */
  public Optional<ReasoningEffort> getReasoningEffort() {
    return Optional.ofNullable(reasoningEffort);
  }
}
