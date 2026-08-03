package net.sahibnanda.portfolio.templates;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import java.util.List;
import java.util.Map;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.enums.ContextType;
import org.springframework.stereotype.Component;

/**
 * Renders prompt text through compile-time JTE templates (never hardcoded
 * strings) in {@code src/main/resources/templates}.
 */
@Component
public final class PromptTemplates {

  /** Precompiled-template engine; templates are compiled at build time. */
  private final TemplateEngine templateEngine =
      TemplateEngine.createPrecompiled(ContentType.Plain);

  /**
   * Renders the Orchestrator AI's system prompt, listing every
   * {@link ContextType} and its description.
   *
   * @return the rendered system prompt
   */
  public String getSystemPromptForOrchestratorAI() {
    StringOutput output = new StringOutput();
    templateEngine.render("orchestrator-system.jte",
        Map.of("contextTypes", List.of(ContextType.values())), output);
    return output.toString();
  }

  /**
   * Renders the Orchestrator AI's user-turn prompt: prior conversation history
   * followed by the current message.
   *
   * @param conversationHistory prior messages in the conversation, oldest first
   * @param currentMessage the user's latest message
   * @return the rendered user prompt
   */
  public String getUserPromptForOrchestratorAI(
      final List<Message> conversationHistory, final String currentMessage) {
    StringOutput output = new StringOutput();
    templateEngine.render("orchestrator-user.jte", Map.of("conversationHistory",
        conversationHistory, "currentMessage", currentMessage), output);
    return output.toString();
  }

  /**
   * Renders the Worker AI's system prompt.
   *
   * @return the rendered system prompt
   */
  public String getSystemPromptForWorkerAI() {
    StringOutput output = new StringOutput();
    templateEngine.render("responder-system.jte", Map.of(), output);
    return output.toString();
  }

  /**
   * Renders the Worker AI's user-turn prompt: aggregated context, prior
   * conversation history, then the current message.
   *
   * @param conversationHistory prior messages in the conversation, oldest first
   * @param currentMessage the user's latest message
   * @param aggregatedContext the pre-formatted, compact context text from
   *        {@link net.sahibnanda.portfolio.services.ContextAggregatorService
   *        #aggregate(java.util.List)}
   * @return the rendered user prompt
   */
  public String getUserPromptForWorkerAI(
      final List<Message> conversationHistory, final String currentMessage,
      final String aggregatedContext) {
    StringOutput output = new StringOutput();
    templateEngine
        .render("responder-user.jte",
            Map.of("conversationHistory", conversationHistory, "currentMessage",
                currentMessage, "aggregatedContext", aggregatedContext),
            output);
    return output.toString();
  }
}
