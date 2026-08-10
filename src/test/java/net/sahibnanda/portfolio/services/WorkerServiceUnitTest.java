package net.sahibnanda.portfolio.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.enums.ContextType;
import net.sahibnanda.portfolio.options.LLMCallOptions;
import net.sahibnanda.portfolio.templates.PromptTemplates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pure-mock unit tests for {@link WorkerService}, exercising
 * {@link WorkerService#respond(List, List, String)} and
 * {@link WorkerService#respondStream(List, List, String, Consumer)} against
 * mocked constructor dependencies so no real LLM call, prompt template
 * rendering, or infrastructure is used. Unlike {@link WorkerServiceTest}, these
 * tests run unconditionally in CI.
 */
class WorkerServiceUnitTest {

  private static final String SYSTEM_PROMPT = "rendered system prompt";
  private static final String USER_PROMPT = "rendered user prompt";
  private static final String AGGREGATED_CONTEXT = "aggregated context text";
  private static final String OWNER_NAME = "Sahib Nanda";
  private static final String USER_MESSAGE = "What is your LeetCode rating?";

  private LLMService llmService;
  private PromptTemplates promptTemplates;
  private ContextAggregatorService contextAggregatorService;
  private DetailsService detailsService;
  private WorkerService workerService;

  @BeforeEach
  void setup() {
    llmService = mock(LLMService.class);
    promptTemplates = mock(PromptTemplates.class);
    contextAggregatorService = mock(ContextAggregatorService.class);
    detailsService = mock(DetailsService.class);
    workerService = new WorkerService(llmService, promptTemplates,
        contextAggregatorService, detailsService);

    when(detailsService.getPortfolioOwnerName()).thenReturn(OWNER_NAME);
    when(contextAggregatorService.aggregate(any()))
        .thenReturn(AGGREGATED_CONTEXT);
    when(promptTemplates.getSystemPromptForWorkerAI(OWNER_NAME))
        .thenReturn(SYSTEM_PROMPT);
    when(promptTemplates.getUserPromptForWorkerAI(any(), any(), any()))
        .thenReturn(USER_PROMPT);
  }

  @Test
  void respondStreamBuildsTheExactSameSystemAndUserPromptsAsRespond() {
    List<ContextType> requiredContexts = List.of(ContextType.LEETCODE);
    List<Message> conversationHistory = List.of();

    when(llmService.call(any(LLMCallOptions.class))).thenReturn("reply");
    when(llmService.callStream(any(LLMCallOptions.class), any()))
        .thenReturn("streamed reply");

    workerService.respond(requiredContexts, conversationHistory, USER_MESSAGE);
    workerService.respondStream(requiredContexts, conversationHistory,
        USER_MESSAGE, token -> {
        });

    ArgumentCaptor<LLMCallOptions> callOptionsCaptor =
        ArgumentCaptor.forClass(LLMCallOptions.class);
    verify(llmService).call(callOptionsCaptor.capture());

    ArgumentCaptor<LLMCallOptions> callStreamOptionsCaptor =
        ArgumentCaptor.forClass(LLMCallOptions.class);
    verify(llmService).callStream(callStreamOptionsCaptor.capture(), any());

    LLMCallOptions respondOptions = callOptionsCaptor.getValue();
    LLMCallOptions respondStreamOptions = callStreamOptionsCaptor.getValue();

    assertThat(respondStreamOptions.getSystemPrompt())
        .isEqualTo(respondOptions.getSystemPrompt());
    assertThat(respondStreamOptions.getUserPrompt())
        .isEqualTo(respondOptions.getUserPrompt());
    assertThat(respondOptions.getSystemPrompt()).isEqualTo(SYSTEM_PROMPT);
    assertThat(respondOptions.getUserPrompt()).isEqualTo(USER_PROMPT);
  }

  @Test
  void respondStreamDelegatesToLlmServiceCallStreamAndForwardsOnToken() {
    List<String> collected = new ArrayList<>();
    Consumer<String> onToken = collected::add;

    when(llmService.callStream(any(LLMCallOptions.class), any()))
        .thenAnswer(invocation -> {
          Consumer<String> forwarded = invocation.getArgument(1);
          forwarded.accept("hello");
          return "hello";
        });

    workerService.respondStream(List.of(ContextType.NONE), List.of(),
        USER_MESSAGE, onToken);

    verify(llmService).callStream(any(LLMCallOptions.class), any());
    assertThat(collected).containsExactly("hello");
  }

  @Test
  void respondStreamReturnsWhateverLlmServiceCallStreamReturns() {
    when(llmService.callStream(any(LLMCallOptions.class), any()))
        .thenReturn("the full accumulated reply");

    String result = workerService.respondStream(List.of(ContextType.NONE),
        List.of(), USER_MESSAGE, token -> {
        });

    assertThat(result).isEqualTo("the full accumulated reply");
  }

  @Test
  void respondStreamThrowsIllegalArgumentExceptionWhenUserMessageIsBlankAndNeverCallsLlmService() {
    assertThatThrownBy(() -> workerService.respondStream(List.of(), List.of(),
        "   ", token -> {
        })).isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(llmService);
  }

  @Test
  void respondBehaviorIsUnchangedAfterTheRefactor() {
    when(llmService.call(any(LLMCallOptions.class)))
        .thenReturn("the final answer");

    String result = workerService.respond(List.of(ContextType.LEETCODE),
        List.of(), USER_MESSAGE);

    ArgumentCaptor<LLMCallOptions> callOptionsCaptor =
        ArgumentCaptor.forClass(LLMCallOptions.class);
    verify(llmService).call(callOptionsCaptor.capture());

    assertThat(result).isEqualTo("the final answer");
    assertThat(callOptionsCaptor.getValue().getSystemPrompt())
        .isEqualTo(SYSTEM_PROMPT);
    assertThat(callOptionsCaptor.getValue().getUserPrompt())
        .isEqualTo(USER_PROMPT);
  }
}
