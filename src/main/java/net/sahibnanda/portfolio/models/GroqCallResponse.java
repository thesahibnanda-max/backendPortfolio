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

  private String id;
  private String object;
  private Long created;
  private String model;
  private List<Choice> choices;
  private Usage usage;
  private String systemFingerprint;
  private XGroq xGroq;
  private String serviceTier;

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Choice {
    private Integer index;
    private Message message;
    private Delta delta;
    private String finishReason;
    private Object logprobs;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Message {
    private String role;
    private String content;
    private List<ToolCall> toolCalls;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Delta {
    private String role;
    private String content;
    private List<ToolCall> toolCalls;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Usage {
    private Double queueTime;
    private Integer promptTokens;
    private Double promptTime;
    private Integer completionTokens;
    private Double completionTime;
    private Integer totalTokens;
    private Double totalTime;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class XGroq {
    private String id;
    private Integer seed;
    private Usage usage;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class ToolCall {
    private String id;
    private String type;
    private Function function;
  }

  @Builder
  @Jacksonized
  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Function {
    private String name;
    private String arguments;
  }
}
