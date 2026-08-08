package net.sahibnanda.portfolio.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.sahibnanda.portfolio.entity.Message;

/**
 * Bounds conversation history sent to the LLM per turn, guarding against
 * unbounded per-turn resend cost as a chat grows. The persisted chat is never
 * affected -- only the copy handed to the Orchestrator/Worker AIs.
 */
@UtilityClass
public final class ConversationHistoryUtils {

  /**
   * Keeps at most the last {@code maxMessages} messages (oldest dropped first),
   * then, if their combined character length still exceeds {@code maxChars},
   * drops further from the oldest end until it fits -- except the single most
   * recent message is always kept even if it alone exceeds {@code maxChars}, so
   * the result is never empty when the input isn't.
   *
   * @param history full stored history, oldest first
   * @param maxMessages maximum number of messages to keep
   * @param maxChars maximum combined character budget of the kept messages
   * @return a new list, oldest first, satisfying both bounds where possible
   */
  public List<Message> truncate(final List<Message> history,
      final int maxMessages, final int maxChars) {
    if (history.isEmpty()) {
      return List.of();
    }
    List<Message> byCount = history.size() <= maxMessages ? history
        : history.subList(history.size() - maxMessages, history.size());

    Deque<Message> kept = new ArrayDeque<>(byCount);
    int totalChars = kept.stream().mapToInt(m -> m.message().length()).sum();
    while (totalChars > maxChars && kept.size() > 1) {
      totalChars -= kept.removeFirst().message().length();
    }
    return List.copyOf(kept);
  }
}
