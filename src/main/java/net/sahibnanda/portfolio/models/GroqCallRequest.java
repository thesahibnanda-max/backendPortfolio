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

  private List<Message> messages;
  private String model;
  private double temperature;
  private int maxCompletionTokens;
  private double topP;
  private boolean stream;
  private List<String> stop;
  private String reasoningEffort;

  @Builder
  @Data
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Message {
    private String role;
    private String content;
  }
}
