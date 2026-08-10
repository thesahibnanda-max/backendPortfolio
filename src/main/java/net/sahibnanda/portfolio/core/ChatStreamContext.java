package net.sahibnanda.portfolio.core;

import java.util.List;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.enums.ContextType;

/**
 * An in-process handoff between two {@code Core} methods during chat streaming.
 *
 * @param authenticated whether the caller is authenticated
 * @param callerId the resolved username or anonymous session id
 * @param chatId the chat identifier
 * @param userMessage the caller's message text
 * @param boundedHistory the truncated conversation history
 * @param requiredContexts the knowledge domains required to answer the question
 */
public record ChatStreamContext(
    boolean authenticated,
    String callerId,
    String chatId,
    String userMessage,
    List<Message> boundedHistory,
    List<ContextType> requiredContexts) {
}
