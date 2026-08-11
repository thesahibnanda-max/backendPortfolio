package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.sahibnanda.portfolio.client.GroqClient;
import net.sahibnanda.portfolio.config.LLMProperties;
import net.sahibnanda.portfolio.enums.GroqModel;
import net.sahibnanda.portfolio.models.GroqCallRequest;
import net.sahibnanda.portfolio.models.GroqCallResponse;
import net.sahibnanda.portfolio.options.LLMCallOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pure-mock unit tests for {@link LLMService}, exercising
 * {@link LLMService#call(LLMCallOptions)} and
 * {@link LLMService#callStream(LLMCallOptions, Consumer)} against a mocked
 * {@link GroqClient} so no real Groq API call is made. Unlike
 * {@link LLMServiceTest}, these tests run unconditionally in CI.
 */
class LLMServiceUnitTest {

  private GroqClient groqClient;
  private LLMService llmService;

  @BeforeEach
  void setup() {
    groqClient = mock(GroqClient.class);
    LLMProperties llmProperties = new LLMProperties(
        Map.of(GroqModel.LLAMA_3_3_70B, 100), 0.5, 0.5, 1.0, 1.0, 64);
    llmService = new LLMService(groqClient, llmProperties);
  }

  @Test
  void callStreamBuildsRequestWithStreamTrueAndCallsGroqClientCallStream() {
    when(groqClient.callStream(any(GroqCallRequest.class), any()))
        .thenReturn("reply");

    llmService.callStream(LLMCallOptions.builder().systemPrompt("system prompt")
        .userPrompt("user prompt").build(), token -> {
        });

    ArgumentCaptor<GroqCallRequest> requestCaptor =
        ArgumentCaptor.forClass(GroqCallRequest.class);
    verify(groqClient).callStream(requestCaptor.capture(), any());
    verifyNoMoreInteractions(groqClient);

    assertThat(requestCaptor.getValue().isStream()).isTrue();
  }

  @Test
  void callStreamForwardsTheCallersOnTokenCallbackUnchanged() {
    List<String> collected = new ArrayList<>();
    Consumer<String> onToken = collected::add;

    when(groqClient.callStream(any(GroqCallRequest.class), eq(onToken)))
        .thenAnswer(invocation -> {
          Consumer<String> forwarded = invocation.getArgument(1);
          forwarded.accept("hello");
          return "hello";
        });

    llmService.callStream(LLMCallOptions.builder().systemPrompt("system prompt")
        .userPrompt("user prompt").build(), onToken);

    assertThat(collected).containsExactly("hello");
  }

  @Test
  void callStreamReturnsWhateverGroqClientCallStreamReturns() {
    when(groqClient.callStream(any(GroqCallRequest.class), any()))
        .thenReturn("the full accumulated reply");

    String result = llmService.callStream(LLMCallOptions.builder()
        .systemPrompt("system prompt").userPrompt("user prompt").build(),
        token -> {
        });

    assertThat(result).isEqualTo("the full accumulated reply");
  }

  @Test
  void callStreamAppliesTheGivenTemperatureAndTopPToTheCapturedRequest() {
    when(groqClient.callStream(any(GroqCallRequest.class), any()))
        .thenReturn("reply");

    llmService.callStream(
        LLMCallOptions.builder().systemPrompt("system prompt")
            .userPrompt("user prompt").temperature(0.2).topP(0.9).build(),
        token -> {
        });

    ArgumentCaptor<GroqCallRequest> requestCaptor =
        ArgumentCaptor.forClass(GroqCallRequest.class);
    verify(groqClient).callStream(requestCaptor.capture(), any());

    GroqCallRequest captured = requestCaptor.getValue();
    assertThat(captured.getTemperature()).isEqualTo(0.2);
    assertThat(captured.getTopP()).isEqualTo(0.9);
    assertThat(captured.getModel())
        .isEqualTo(GroqModel.LLAMA_3_3_70B.getModelId());
  }

  @Test
  void callStillReturnsExtractedContentAndSendsStreamFalseToGroqClientCall() {
    GroqCallResponse response =
        GroqCallResponse.builder()
            .choices(
                List.of(
                    GroqCallResponse.Choice.builder()
                        .message(GroqCallResponse.Message.builder()
                            .content("extracted reply").build())
                        .build()))
            .build();
    when(groqClient.call(any(GroqCallRequest.class))).thenReturn(response);

    String result = llmService.call(LLMCallOptions.builder()
        .systemPrompt("system prompt").userPrompt("user prompt").build());

    ArgumentCaptor<GroqCallRequest> requestCaptor =
        ArgumentCaptor.forClass(GroqCallRequest.class);
    verify(groqClient).call(requestCaptor.capture());
    verifyNoMoreInteractions(groqClient);

    assertThat(result).isEqualTo("extracted reply");
    assertThat(requestCaptor.getValue().isStream()).isFalse();
  }

  @Test
  void callWithToolsSendsStreamFalseAndOmitsToolsWhenNoneGiven() {
    GroqCallResponse response = GroqCallResponse.builder()
        .choices(List.of(GroqCallResponse.Choice.builder().message(
            GroqCallResponse.Message.builder().content("final answer").build())
            .build()))
        .build();
    when(groqClient.call(any(GroqCallRequest.class))).thenReturn(response);

    List<GroqCallRequest.Message> messages = List.of(
        GroqCallRequest.Message.builder().role("system").content("sys").build(),
        GroqCallRequest.Message.builder().role("user").content("hi").build());

    GroqCallResponse result = llmService.callWithTools(messages, List.of());

    ArgumentCaptor<GroqCallRequest> requestCaptor =
        ArgumentCaptor.forClass(GroqCallRequest.class);
    verify(groqClient).call(requestCaptor.capture());
    verifyNoMoreInteractions(groqClient);

    assertThat(result).isSameAs(response);
    assertThat(requestCaptor.getValue().isStream()).isFalse();
    assertThat(requestCaptor.getValue().getTools()).isNull();
    assertThat(requestCaptor.getValue().getMessages()).isEqualTo(messages);
  }

  @Test
  void callWithToolsAttachesTheGivenToolsWhenNonEmpty() {
    GroqCallResponse response = GroqCallResponse.builder()
        .choices(List.of(GroqCallResponse.Choice.builder().message(
            GroqCallResponse.Message.builder().content("final answer").build())
            .build()))
        .build();
    when(groqClient.call(any(GroqCallRequest.class))).thenReturn(response);

    GroqCallRequest.Tool tool =
        GroqCallRequest.Tool.builder().type("function")
            .function(GroqCallRequest.ToolFunction.builder()
                .name("get_leetcode_details").description("desc")
                .parameters(Map.of()).build())
            .build();
    List<GroqCallRequest.Message> messages = List.of(
        GroqCallRequest.Message.builder().role("system").content("sys").build(),
        GroqCallRequest.Message.builder().role("user").content("hi").build());

    llmService.callWithTools(messages, List.of(tool));

    ArgumentCaptor<GroqCallRequest> requestCaptor =
        ArgumentCaptor.forClass(GroqCallRequest.class);
    verify(groqClient).call(requestCaptor.capture());

    assertThat(requestCaptor.getValue().getTools()).containsExactly(tool);
  }
}
