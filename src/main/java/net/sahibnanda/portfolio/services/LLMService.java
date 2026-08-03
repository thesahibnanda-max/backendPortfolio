package net.sahibnanda.portfolio.services;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.client.GroqClient;
import net.sahibnanda.portfolio.config.LLMProperties;
import net.sahibnanda.portfolio.enums.GroqModel;
import net.sahibnanda.portfolio.exception.GroqCallException;
import net.sahibnanda.portfolio.models.GroqCallRequest;
import net.sahibnanda.portfolio.models.GroqCallResponse;
import net.sahibnanda.portfolio.options.LLMCallOptions;
import net.sahibnanda.portfolio.utils.RandomUtils;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Composes {@link GroqClient} into a convenience layer: picks a Groq model at
 * random (weighted by {@link LLMProperties#modelWeights()}), fills in
 * temperature/top-p (given, or randomized within the configured range), builds
 * the two-message system+user request, and extracts the reply text from the raw
 * nested {@link GroqCallResponse}.
 */
@Slf4j
@Service
public final class LLMService {

  /** Role label for the system-prompt message. */
  private static final String ROLE_SYSTEM = "system";

  /** Role label for the user-prompt message. */
  private static final String ROLE_USER = "user";

  /** Client used to make the real Groq chat-completion call. */
  private final GroqClient groqClient;

  /** Model weights and sampling-range defaults. */
  private final LLMProperties properties;

  /**
   * Creates a new LLM service.
   *
   * @param groqApiClient client used to make the real Groq chat-completion call
   * @param llmProperties model weights and sampling-range defaults
   */
  public LLMService(final GroqClient groqApiClient,
      final LLMProperties llmProperties) {
    this.groqClient =
        Objects.requireNonNull(groqApiClient, "groqApiClient must not be null");
    this.properties =
        Objects.requireNonNull(llmProperties, "llmProperties must not be null");
  }

  /**
   * Requests a chat completion from a randomly chosen Groq model.
   *
   * @param options the system/user prompts, plus optional temperature/top-p
   *        overrides
   * @return the completion's reply text
   * @throws NullPointerException if {@code options} is null
   * @throws IllegalArgumentException if the system or user prompt is blank
   * @throws net.sahibnanda.portfolio.exception.GroqCallException if the call
   *         fails, or the response contains no choices
   */
  public String call(final LLMCallOptions options) {
    Objects.requireNonNull(options, "options must not be null");
    requireNonBlank(options.getSystemPrompt(), "systemPrompt");
    requireNonBlank(options.getUserPrompt(), "userPrompt");

    GroqModel model = selectModel();
    double temperature =
        options.getTemperature() != null ? options.getTemperature()
            : RandomUtils.randomInRange(properties.minTemperature(),
                properties.maxTemperature());
    double topP = options.getTopP() != null ? options.getTopP()
        : RandomUtils.randomInRange(properties.minTopP(), properties.maxTopP());

    GroqCallRequest.GroqCallRequestBuilder requestBuilder = GroqCallRequest
        .builder().model(model.getModelId())
        .messages(List.of(
            GroqCallRequest.Message.builder().role(ROLE_SYSTEM)
                .content(options.getSystemPrompt()).build(),
            GroqCallRequest.Message.builder().role(ROLE_USER)
                .content(options.getUserPrompt()).build()))
        .temperature(temperature).topP(topP)
        .maxCompletionTokens(properties.maxCompletionTokens()).stream(false);
    model.getReasoningEffort()
        .ifPresent(effort -> requestBuilder.reasoningEffort(effort.getValue()));

    GroqCallResponse response = groqClient.call(requestBuilder.build());
    return extractContent(response);
  }

  private GroqModel selectModel() {
    return RandomUtils.weightedRandom(properties.modelWeights());
  }

  private static String extractContent(final GroqCallResponse response) {
    if (response.getChoices() == null || response.getChoices().isEmpty()) {
      log.error("Groq response contained no choices: {}", response);
      throw new GroqCallException("Groq response contained no choices.");
    }
    return response.getChoices().getFirst().getMessage().getContent();
  }

  private static void requireNonBlank(final String value,
      final String fieldName) {
    if (StringUtils.isEmpty(value)) {
      throw new IllegalArgumentException(fieldName + " is required.");
    }
  }
}
