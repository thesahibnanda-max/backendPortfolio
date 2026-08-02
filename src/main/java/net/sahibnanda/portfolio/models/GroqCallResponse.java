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
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GroqCallResponse {

  /** Unique identifier for this chat completion. */
  private String id;
  /** Object type, e.g. "chat.completion". */
  private String object;
  /** Unix timestamp of when the completion was created. */
  private Long created;
  /** Name of the Groq model used to generate the completion. */
  private String model;
  /** List of generated completion choices. */
  private List<Choice> choices;
  /** Token usage statistics for the request. */
  private Usage usage;
  /** Fingerprint identifying the backend system configuration. */
  private String systemFingerprint;
  /** Groq-specific metadata about the request. */
  private XGroq xGroq;
  /** Service tier used to process the request. */
  private String serviceTier;

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Choice {
    /** Index of this choice within the choices list. */
    private Integer index;
    /** Complete message for a non-streamed response choice. */
    private Message message;
    /** Incremental message content for a streamed response. */
    private Delta delta;
    /** Reason the model stopped generating tokens. */
    private String finishReason;
    /** Log probability information for the choice, if requested. */
    private Object logprobs;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Message {
    /** Role of the message author, e.g. "assistant". */
    private String role;
    /** Text content of the message. */
    private String content;
    /** Tool calls requested by the assistant, if any. */
    private List<ToolCall> toolCalls;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Delta {
    /** Role of the message author, e.g. "assistant". */
    private String role;
    /** Incremental text content of the message. */
    private String content;
    /** Tool calls requested by the assistant, if any. */
    private List<ToolCall> toolCalls;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Usage {
    /** Time in seconds the request spent queued. */
    private Double queueTime;
    /** Number of tokens in the prompt. */
    private Integer promptTokens;
    /** Time in seconds spent processing the prompt. */
    private Double promptTime;
    /** Number of tokens in the generated completion. */
    private Integer completionTokens;
    /** Time in seconds spent generating the completion. */
    private Double completionTime;
    /** Total number of tokens used, prompt plus completion. */
    private Integer totalTokens;
    /** Total time in seconds spent handling the request. */
    private Double totalTime;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class XGroq {
    /** Groq-internal identifier for the request. */
    private String id;
    /** Random seed used for generation, if applicable. */
    private Integer seed;
    /** Token usage statistics reported by Groq. */
    private Usage usage;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class ToolCall {
    /** Unique identifier for this tool call. */
    private String id;
    /** Type of the tool call, e.g. "function". */
    private String type;
    /** Function invocation details for this tool call. */
    private Function function;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Function {
    /** Name of the function to invoke. */
    private String name;
    /** JSON-encoded arguments to pass to the function. */
    private String arguments;
  }
}
