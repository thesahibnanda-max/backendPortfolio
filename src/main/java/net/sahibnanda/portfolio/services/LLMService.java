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
    GroqCallRequest.GroqCallRequestBuilder requestBuilder =
        buildRequest(options);
    GroqCallResponse response =
        groqClient.call(requestBuilder.stream(false).build());
    return extractContent(response);
  }

  /**
   * Requests a chat completion from a randomly chosen Groq model, streaming the
   * reply incrementally as it arrives.
   *
   * @param options the system/user prompts, plus optional temperature/top-p
   *        overrides
   * @param onToken callback invoked once per non-empty content chunk received
   *        from the stream, in arrival order
   * @return the full completion's reply text, formed by concatenating every
   *         content chunk delivered to {@code onToken}
   * @throws NullPointerException if {@code options} is null
   * @throws IllegalArgumentException if the system or user prompt is blank
   * @throws net.sahibnanda.portfolio.exception.GroqCallException if the call
   *         fails
   */
  public String callStream(final LLMCallOptions options,
      final java.util.function.Consumer<String> onToken) {
    GroqCallRequest.GroqCallRequestBuilder requestBuilder =
        buildRequest(options);
    return groqClient.callStream(requestBuilder.stream(true).build(), onToken);
  }

  /**
   * Requests a chat completion for an already-built, multi-turn message list,
   * optionally offering {@code tools} for the model to call -- used by
   * {@link McpAiService}'s tool-calling loop, which needs the raw
   * {@link GroqCallResponse} (to inspect
   * {@code finishReason}/{@code toolCalls}), not just the extracted reply text
   * {@link #call} returns. Always non-streaming -- see {@link McpAiService}'s
   * class Javadoc for why.
   *
   * @param messages the full conversation so far, oldest first, including the
   *        system prompt
   * @param tools the tools the model may call, or {@code null}/empty for none
   * @return the raw Groq response
   * @throws NullPointerException if {@code messages} is null
   * @throws IllegalArgumentException if {@code messages} is empty
   * @throws net.sahibnanda.portfolio.exception.GroqCallException if the call
   *         fails
   */
  public GroqCallResponse callWithTools(
      final List<GroqCallRequest.Message> messages,
      final List<GroqCallRequest.Tool> tools) {
    Objects.requireNonNull(messages, "messages must not be null");
    if (messages.isEmpty()) {
      throw new IllegalArgumentException("messages must not be empty.");
    }

    ModelSelection selection = resolveModelAndSampling(null, null);
    GroqCallRequest.GroqCallRequestBuilder requestBuilder = GroqCallRequest
        .builder().model(selection.model().getModelId()).messages(messages)
        .temperature(selection.temperature()).topP(selection.topP())
        .maxCompletionTokens(properties.maxCompletionTokens()).stream(false);
    if (tools != null && !tools.isEmpty()) {
      requestBuilder.tools(tools);
    }
    selection.model().getReasoningEffort()
        .ifPresent(effort -> requestBuilder.reasoningEffort(effort.getValue()));

    return groqClient.call(requestBuilder.build());
  }

  /**
   * Validates the given options and builds the shared portions of a Groq chat
   * completion request: model selection, temperature/top-p resolution, the
   * system+user messages list, max completion tokens, and (if the selected
   * model supports one) reasoning effort. The returned builder's {@code stream}
   * flag is left unset; callers must set it before building the request.
   *
   * @param options the system/user prompts, plus optional temperature/top-p
   *        overrides
   * @return a request builder populated with everything but the {@code stream}
   *         flag
   * @throws NullPointerException if {@code options} is null
   * @throws IllegalArgumentException if the system or user prompt is blank
   */
  private GroqCallRequest.GroqCallRequestBuilder buildRequest(
      final LLMCallOptions options) {
    Objects.requireNonNull(options, "options must not be null");
    requireNonBlank(options.getSystemPrompt(), "systemPrompt");
    requireNonBlank(options.getUserPrompt(), "userPrompt");

    ModelSelection selection =
        resolveModelAndSampling(options.getTemperature(), options.getTopP());

    GroqCallRequest.GroqCallRequestBuilder requestBuilder =
        GroqCallRequest.builder().model(selection.model().getModelId())
            .messages(List.of(
                GroqCallRequest.Message.builder().role(ROLE_SYSTEM)
                    .content(options.getSystemPrompt()).build(),
                GroqCallRequest.Message.builder().role(ROLE_USER)
                    .content(options.getUserPrompt()).build()))
            .temperature(selection.temperature()).topP(selection.topP())
            .maxCompletionTokens(properties.maxCompletionTokens());
    selection.model().getReasoningEffort()
        .ifPresent(effort -> requestBuilder.reasoningEffort(effort.getValue()));

    return requestBuilder;
  }

  /**
   * Picks a random weighted model and resolves the temperature/top-p to send,
   * using the given overrides if non-null or a random value within the
   * configured range otherwise. Shared by {@link #buildRequest} and
   * {@link #callWithTools}.
   *
   * @param temperatureOverride an explicit temperature, or {@code null} to
   *        randomize it
   * @param topPOverride an explicit top-p, or {@code null} to randomize it
   * @return the selected model and resolved sampling parameters
   */
  private ModelSelection resolveModelAndSampling(
      final Double temperatureOverride, final Double topPOverride) {
    GroqModel model = selectModel();
    double temperature = temperatureOverride != null ? temperatureOverride
        : RandomUtils.randomInRange(properties.minTemperature(),
            properties.maxTemperature());
    double topP = topPOverride != null ? topPOverride
        : RandomUtils.randomInRange(properties.minTopP(), properties.maxTopP());
    return new ModelSelection(model, temperature, topP);
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

  /**
   * The model and resolved temperature/top-p picked for one Groq call.
   *
   * @param model the selected model
   * @param temperature the resolved temperature
   * @param topP the resolved top-p
   */
  private record ModelSelection(GroqModel model, double temperature,
      double topP) {
  }
}
