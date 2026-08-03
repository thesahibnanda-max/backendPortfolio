package net.sahibnanda.portfolio.templates;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.entity.Role;
import net.sahibnanda.portfolio.enums.ContextType;
import org.junit.jupiter.api.Test;

class PromptTemplatesTest {

  private final PromptTemplates promptTemplates = new PromptTemplates();

  @Test
  void systemPromptListsEveryContextTypeAndTheJsonContract() {
    String systemPrompt = promptTemplates.getSystemPromptForOrchestratorAI();

    for (ContextType contextType : ContextType.values()) {
      assertThat(systemPrompt).contains(contextType.name());
    }
    assertThat(systemPrompt).contains("requiredContexts").contains("reason");
  }

  @Test
  void userPromptWithNoHistoryContainsOnlyCurrentMessage() {
    String userPrompt = promptTemplates
        .getUserPromptForOrchestratorAI(List.of(), "What is binary search?");

    assertThat(userPrompt).contains("What is binary search?");
    assertThat(userPrompt).doesNotContain("Conversation so far");
  }

  @Test
  void userPromptWithHistoryContainsEveryPriorMessageAndCurrentMessage() {
    List<Message> history = List.of(
        new Message(Role.USER, "hi", Instant.parse("2026-08-02T12:00:00Z")),
        new Message(Role.ASSISTANT, "hello there",
            Instant.parse("2026-08-02T12:00:05Z")));

    String userPrompt = promptTemplates.getUserPromptForOrchestratorAI(history,
        "What is your LeetCode rating?");

    assertThat(userPrompt).contains("Conversation so far");
    assertThat(userPrompt).contains("hi").contains("hello there");
    assertThat(userPrompt).contains("What is your LeetCode rating?");
  }
}
