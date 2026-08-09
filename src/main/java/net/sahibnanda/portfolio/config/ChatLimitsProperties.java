package net.sahibnanda.portfolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Caps on caller-supplied chat input sizes and per-turn LLM context, guarding
 * against abusive oversized prompts and unbounded per-turn LLM cost as a chat
 * grows. Product configuration, not secret -- set directly in application.yml
 * rather than sourced from an environment variable.
 *
 * @param maxMessageLength maximum characters allowed in a single
 *        {@code userPrompt} message
 * @param maxChatTitleLength maximum characters allowed in a chat title
 * @param maxSearchQueryLength maximum characters allowed in a search query
 * @param maxHistoryMessages maximum number of the most recent messages sent to
 *        the LLM per turn, regardless of how many are actually stored
 * @param maxHistoryChars maximum combined character budget of the messages sent
 *        to the LLM per turn, applied on top of {@code maxHistoryMessages}
 * @param maxMessagesPerChat maximum number of messages a single chat may
 *        accumulate before {@code userPrompt} refuses to add more, forcing the
 *        caller to start a new chat
 */
@ConfigurationProperties(prefix = "chat-limits")
public record ChatLimitsProperties(int maxMessageLength, int maxChatTitleLength,
    int maxSearchQueryLength, int maxHistoryMessages, int maxHistoryChars,
    int maxMessagesPerChat) {
}
