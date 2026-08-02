package net.sahibnanda.portfolio.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GroqCallRequest {

  /** Conversation messages to send to the model. */
  private List<Message> messages;
  /** Name of the Groq model to use for completion. */
  private String model;
  /** Sampling temperature controlling response randomness. */
  private double temperature;
  /** Maximum number of tokens to generate in the completion. */
  private int maxCompletionTokens;
  /** Nucleus sampling probability mass to consider. */
  private double topP;
  /** Whether to stream partial completion results. */
  private boolean stream;
  /** Sequences at which generation should stop. */
  private List<String> stop;
  /** Level of reasoning effort requested from the model. */
  private String reasoningEffort;

  @Builder
  @Data
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Message {
    /** Role of the message author, e.g. "user". */
    private String role;
    /** Text content of the message. */
    private String content;
  }
}
