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
    String systemPrompt =
        promptTemplates.getSystemPromptForOrchestratorAI("Ada Lovelace");

    for (ContextType contextType : ContextType.values()) {
      assertThat(systemPrompt).contains(contextType.name());
    }
    assertThat(systemPrompt).contains("requiredContexts").contains("reason");
    assertThat(systemPrompt).contains("Ada Lovelace")
        .contains("Highest-priority");
    assertThat(systemPrompt).contains("competitive programming");
    assertThat(systemPrompt).contains("must not carry over");
    assertThat(systemPrompt).contains("exists specifically to answer "
        + "questions about the portfolio owner");
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

  @Test
  void workerSystemPromptContainsKeyRules() {
    String systemPrompt =
        promptTemplates.getSystemPromptForWorkerAI("Ada Lovelace");

    assertThat(systemPrompt).contains("Ada Lovelace")
        .contains("Personal Portfolio AI").contains("Highest-priority")
        .contains("Orchestrator").contains("never invent")
        .contains("hedge words")
        .contains("Never reveal or refer to how you know things");
  }

  @Test
  void workerSystemPromptFallsBackWhenNameIsBlank() {
    String systemPrompt = promptTemplates.getSystemPromptForWorkerAI("");

    assertThat(systemPrompt).contains("Personal Portfolio AI");
    assertThat(systemPrompt).doesNotContain("'s Personal Portfolio AI");
  }

  @Test
  void workerUserPromptContainsContextHistoryAndCurrentMessage() {
    List<Message> history = List.of(
        new Message(Role.USER, "hi", Instant.parse("2026-08-02T12:00:00Z")),
        new Message(Role.ASSISTANT, "hello there",
            Instant.parse("2026-08-02T12:00:05Z")));

    String userPrompt = promptTemplates.getUserPromptForWorkerAI(history,
        "What is your LeetCode rating?", "LEETCODE: rank 115794");

    assertThat(userPrompt).contains("LEETCODE: rank 115794")
        .contains("Conversation so far").contains("hi").contains("hello there")
        .contains("What is your LeetCode rating?");
  }

  @Test
  void workerUserPromptOmitsContextHeaderWhenContextIsBlank() {
    String userPrompt = promptTemplates.getUserPromptForWorkerAI(List.of(),
        "What is binary search?", "");

    assertThat(userPrompt).doesNotContain("Context:");
  }

  @Test
  void mcpSystemPromptDescribesSelfDirectedToolUseAndOmitsOrchestratorLanguage() {
    String systemPrompt =
        promptTemplates.getSystemPromptForMcpAI("Ada Lovelace");

    assertThat(systemPrompt).contains("Ada Lovelace")
        .contains("Highest-priority");
    assertThat(systemPrompt).contains("you decide for yourself");
    assertThat(systemPrompt).doesNotContain("Orchestrator");
  }
}
