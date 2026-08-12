package net.sahibnanda.portfolio.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.sahibnanda.portfolio.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class GroqCallRequestTest {

  @Test
  void toolsIsOmittedFromSerializedJsonWhenNotSet() {
    GroqCallRequest request =
        GroqCallRequest.builder().model("llama-3.3-70b-versatile")
            .messages(List.of(GroqCallRequest.Message.builder().role("user")
                .content("hi").build()))
            .temperature(0.5).topP(1.0).maxCompletionTokens(64).stream(false)
            .build();

    String json = JsonUtils.toJson(request);

    assertThat(json).doesNotContain("\"tools\"");
  }

  @Test
  void toolsSerializeAsSnakeCaseFunctionSchemaWhenSet() {
    GroqCallRequest.Tool tool =
        GroqCallRequest.Tool.builder().type("function")
            .function(GroqCallRequest.ToolFunction.builder()
                .name("get_leetcode_details")
                .description("get leetcode details about the user")
                .parameters(Map.of("type", "object", "properties", Map.of()))
                .build())
            .build();
    GroqCallRequest request =
        GroqCallRequest.builder().model("llama-3.3-70b-versatile")
            .messages(List.of(GroqCallRequest.Message.builder().role("user")
                .content("hi").build()))
            .temperature(0.5).topP(1.0).maxCompletionTokens(64).stream(false)
            .tools(List.of(tool)).build();

    String json = JsonUtils.toJson(request);

    assertThat(json).contains("\"tools\"").contains("\"type\":\"function\"")
        .contains("\"name\":\"get_leetcode_details\"")
        .contains("\"parameters\"");
  }

  @Test
  void toolCallIdAndToolCallsSerializeOnAMessageAndAreOmittedWhenUnset() {
    GroqCallRequest.Message plainUserMessage =
        GroqCallRequest.Message.builder().role("user").content("hi").build();
    GroqCallRequest.Message toolResultMessage =
        GroqCallRequest.Message.builder().role("tool").toolCallId("call_123")
            .content("{\"rating\":1800}").build();

    String plainJson = JsonUtils.toJson(plainUserMessage);
    String toolResultJson = JsonUtils.toJson(toolResultMessage);

    assertThat(plainJson).doesNotContain("tool_call_id");
    assertThat(toolResultJson).contains("\"tool_call_id\":\"call_123\"")
        .contains("\"role\":\"tool\"");
  }

  @Test
  void toolCallsSerializesAsSnakeCaseOnAnAssistantMessage() {
    GroqCallResponse.ToolCall toolCall = GroqCallResponse.ToolCall.builder()
        .id("call_1").type("function").function(GroqCallResponse.Function
            .builder().name("get_leetcode_details").arguments("{}").build())
        .build();
    GroqCallRequest.Message assistantMessage = GroqCallRequest.Message.builder()
        .role("assistant").toolCalls(List.of(toolCall)).build();

    String json = JsonUtils.toJson(assistantMessage);

    assertThat(json).contains("\"tool_calls\"").contains("\"id\":\"call_1\"")
        .contains("\"type\":\"function\"")
        .contains("\"name\":\"get_leetcode_details\"")
        .contains("\"arguments\":\"{}\"");
  }
}
