package net.sahibnanda.portfolio.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import net.sahibnanda.portfolio.entity.Message;
import net.sahibnanda.portfolio.entity.Role;
import org.junit.jupiter.api.Test;

class ConversationHistoryUtilsTest {

  @Test
  void truncateReturnsEmptyForEmptyHistory() {
    assertThat(ConversationHistoryUtils.truncate(List.of(), 10, 1000))
        .isEmpty();
  }

  @Test
  void truncateReturnsHistoryUnchangedWhenUnderBothBounds() {
    List<Message> history = messages(3, 10);

    List<Message> result = ConversationHistoryUtils.truncate(history, 10, 1000);

    assertThat(result).isEqualTo(history);
  }

  @Test
  void truncateKeepsOnlyTheLastMessagesWhenOverMessageCount() {
    List<Message> history = messages(5, 10);

    List<Message> result = ConversationHistoryUtils.truncate(history, 2, 1000);

    assertThat(result).hasSize(2);
    assertThat(result).isEqualTo(history.subList(3, 5));
  }

  @Test
  void truncateDropsFromOldestEndWhenOverCharBudget() {
    // 5 messages of 10 chars each = 50 chars; a 25-char budget should keep
    // only the last 2 (20 chars), since keeping 3 would be 30 > 25.
    List<Message> history = messages(5, 10);

    List<Message> result = ConversationHistoryUtils.truncate(history, 10, 25);

    assertThat(result).hasSize(2);
    assertThat(result).isEqualTo(history.subList(3, 5));
  }

  @Test
  void truncateAlwaysKeepsAtLeastTheMostRecentMessage() {
    List<Message> history = messages(3, 100);

    List<Message> result = ConversationHistoryUtils.truncate(history, 10, 1);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst()).isEqualTo(history.getLast());
  }

  private List<Message> messages(final int count, final int messageLength) {
    Instant base = Instant.now();
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(i -> new Message(Role.USER, "x".repeat(messageLength),
            base.plusSeconds(i)))
        .toList();
  }
}
