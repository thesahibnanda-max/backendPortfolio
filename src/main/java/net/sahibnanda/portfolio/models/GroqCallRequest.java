package net.sahibnanda.portfolio.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
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
  /** Tools (MCP-exposed functions) the model may call, or null for none. */
  private List<Tool> tools;

  @Builder
  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Message {
    /** Role of the message author, e.g. "user". */
    private String role;
    /** Text content of the message. */
    private String content;
    /** Id of the tool call this message answers; set only on role "tool". */
    private String toolCallId;
    /** Tool calls this assistant message requested, if any. */
    private List<GroqCallResponse.ToolCall> toolCalls;
  }

  /** A single tool (function) the model may choose to call. */
  @Builder
  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Tool {
    /** The tool's type; always {@code "function"}. */
    private String type;
    /** The function this tool exposes. */
    private ToolFunction function;
  }

  /**
   * A function-calling tool's name, description, and JSON-schema parameters.
   */
  @Builder
  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class ToolFunction {
    /** The function's name, as the model must reference it in a tool call. */
    private String name;
    /** A human/model-readable description of what the function does. */
    private String description;
    /** JSON-schema describing the function's parameters. */
    private Map<String, Object> parameters;
  }
}
