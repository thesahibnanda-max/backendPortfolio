package net.sahibnanda.portfolio.services;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.objects.OrchestratorResponse;
import net.sahibnanda.portfolio.options.LLMCallOptions;
import net.sahibnanda.portfolio.templates.PromptTemplates;
import net.sahibnanda.portfolio.utils.JsonExtractionUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Service;

/**
 * The Orchestrator AI: decides which
 * {@link net.sahibnanda.portfolio.enums.ContextType} knowledge domains are
 * required to answer the user's question. Never answers the question itself,
 * never produces natural language -- only the strict
 * {@link OrchestratorResponse} JSON contract.
 */
@Slf4j
@Service
public final class OrchestratorService {

  /**
   * Low, near-deterministic temperature -- this call must yield strict JSON,
   * not creative variety.
   */
  private static final double TEMPERATURE = 0.0;

  /** Near-greedy top-p, paired with {@link #TEMPERATURE}. */
  private static final double TOP_P = 1.0;

  /** Client used to make the real LLM call. */
  private final LLMService llmService;

  /** Renders the orchestrator's system/user prompts via JTE. */
  private final PromptTemplates promptTemplates;

  /**
   * Creates a new orchestrator service.
   *
   * @param llmServiceClient client used to make the real LLM call
   * @param orchestratorPromptTemplates renders the orchestrator's system/user
   *        prompts via JTE
   */
  public OrchestratorService(final LLMService llmServiceClient,
      final PromptTemplates orchestratorPromptTemplates) {
    this.llmService = Objects.requireNonNull(llmServiceClient,
        "llmServiceClient must not be null");
    this.promptTemplates = Objects.requireNonNull(orchestratorPromptTemplates,
        "orchestratorPromptTemplates must not be null");
  }

  /**
   * Decides which knowledge domains are required to answer the user's latest
   * message.
   *
   * @param conversationHistory prior messages in the conversation, oldest first
   * @param userMessage the user's latest message
   * @return the required contexts and the reason they were selected
   * @throws IllegalArgumentException if {@code userMessage} is blank
   * @throws net.sahibnanda.portfolio.exception.GroqCallException if the LLM
   *         call fails
   * @throws net.sahibnanda.portfolio.exception.JsonExtractionException if the
   *         model's response contains no valid JSON
   */
  public OrchestratorResponse route(final List<Message> conversationHistory,
      final String userMessage) {
    if (StringUtils.isEmpty(userMessage)) {
      throw new IllegalArgumentException("userMessage is required.");
    }

    String systemPrompt = promptTemplates.getSystemPromptForOrchestratorAI();
    String userPrompt = promptTemplates
        .getUserPromptForOrchestratorAI(conversationHistory, userMessage);

    String rawResponse = llmService.call(LLMCallOptions.builder()
        .systemPrompt(systemPrompt).userPrompt(userPrompt)
        .temperature(TEMPERATURE).topP(TOP_P).build());

    return JsonExtractionUtils.extractJson(rawResponse,
        OrchestratorResponse.class);
  }
}
