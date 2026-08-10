package net.sahibnanda.portfolio.services;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.enums.ContextType;
import net.sahibnanda.portfolio.options.LLMCallOptions;
import net.sahibnanda.portfolio.templates.PromptTemplates;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Service;

/**
 * The Worker AI (the PRD's "Responder AI"): answers the user's question using
 * only the context the Orchestrator decided was required. Never decides what
 * context to load itself.
 */
@Slf4j
@Service
public final class WorkerService {

  /** Client used to make the real LLM call. */
  private final LLMService llmService;

  /** Renders the worker's system/user prompts via JTE. */
  private final PromptTemplates promptTemplates;

  /** Loads and formats the domains the Orchestrator selected. */
  private final ContextAggregatorService contextAggregatorService;

  /** Supplies the portfolio owner's name for the system prompt. */
  private final DetailsService detailsService;

  /**
   * Creates a new worker service.
   *
   * @param llmServiceClient client used to make the real LLM call
   * @param workerPromptTemplates renders the worker's system/user prompts via
   *        JTE
   * @param contextAggregator loads and formats the domains the Orchestrator
   *        selected
   * @param detailsClient supplies the portfolio owner's name for the system
   *        prompt
   */
  public WorkerService(final LLMService llmServiceClient,
      final PromptTemplates workerPromptTemplates,
      final ContextAggregatorService contextAggregator,
      final DetailsService detailsClient) {
    this.llmService = Objects.requireNonNull(llmServiceClient,
        "llmServiceClient must not be null");
    this.promptTemplates = Objects.requireNonNull(workerPromptTemplates,
        "workerPromptTemplates must not be null");
    this.contextAggregatorService = Objects.requireNonNull(contextAggregator,
        "contextAggregator must not be null");
    this.detailsService = Objects.requireNonNull(detailsClient,
        "detailsService must not be null");
  }

  /**
   * Answers the user's latest message.
   *
   * @param requiredContexts the domains the Orchestrator AI selected
   * @param conversationHistory prior messages in the conversation, oldest first
   * @param userMessage the user's latest message
   * @return the final natural-language answer
   * @throws IllegalArgumentException if {@code userMessage} is blank
   * @throws net.sahibnanda.portfolio.exception.GroqCallException if the LLM
   *         call fails
   */
  public String respond(final List<ContextType> requiredContexts,
      final List<Message> conversationHistory, final String userMessage) {
    Prompts prompts =
        buildPrompts(requiredContexts, conversationHistory, userMessage);

    return llmService
        .call(LLMCallOptions.builder().systemPrompt(prompts.systemPrompt())
            .userPrompt(prompts.userPrompt()).build());
  }

  /**
   * Answers the user's latest message, streaming the reply incrementally as it
   * arrives.
   *
   * @param requiredContexts the domains the Orchestrator AI selected
   * @param conversationHistory prior messages in the conversation, oldest first
   * @param userMessage the user's latest message
   * @param onToken callback invoked once per non-empty content chunk received
   *        from the stream, in arrival order
   * @return the full final natural-language answer, formed by concatenating
   *         every content chunk delivered to {@code onToken}
   * @throws IllegalArgumentException if {@code userMessage} is blank
   * @throws net.sahibnanda.portfolio.exception.GroqCallException if the LLM
   *         call fails
   */
  public String respondStream(final List<ContextType> requiredContexts,
      final List<Message> conversationHistory, final String userMessage,
      final java.util.function.Consumer<String> onToken) {
    Prompts prompts =
        buildPrompts(requiredContexts, conversationHistory, userMessage);

    return llmService.callStream(
        LLMCallOptions.builder().systemPrompt(prompts.systemPrompt())
            .userPrompt(prompts.userPrompt()).build(),
        onToken);
  }

  /**
   * Validates {@code userMessage} and builds the system/user prompts shared by
   * {@link #respond(List, List, String)} and
   * {@link #respondStream(List, List, String, java.util.function.Consumer)}.
   *
   * @param requiredContexts the domains the Orchestrator AI selected
   * @param conversationHistory prior messages in the conversation, oldest first
   * @param userMessage the user's latest message
   * @return the rendered system and user prompts
   * @throws IllegalArgumentException if {@code userMessage} is blank
   */
  private Prompts buildPrompts(final List<ContextType> requiredContexts,
      final List<Message> conversationHistory, final String userMessage) {
    if (StringUtils.isEmpty(userMessage)) {
      throw new IllegalArgumentException("userMessage is required.");
    }

    String aggregatedContext =
        contextAggregatorService.aggregate(requiredContexts);
    String systemPrompt = promptTemplates
        .getSystemPromptForWorkerAI(detailsService.getPortfolioOwnerName());
    String userPrompt = promptTemplates.getUserPromptForWorkerAI(
        conversationHistory, userMessage, aggregatedContext);

    return new Prompts(systemPrompt, userPrompt);
  }

  /**
   * Holds the rendered system and user prompts shared by
   * {@link #respond(List, List, String)} and
   * {@link #respondStream(List, List, String, java.util.function.Consumer)}.
   *
   * @param systemPrompt the rendered system prompt
   * @param userPrompt the rendered user prompt
   */
  private record Prompts(String systemPrompt, String userPrompt) {
  }
}
