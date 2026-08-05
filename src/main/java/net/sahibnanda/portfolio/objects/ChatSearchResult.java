package net.sahibnanda.portfolio.objects;

import lombok.Builder;
import lombok.Data;

/**
 * One matching chat from
 * {@link net.sahibnanda.portfolio.services.SearchService#processUserQuery},
 * collapsed to its single highest-scoring document.
 */
@Builder
@Data
public final class ChatSearchResult {

  /** The matching chat's id. */
  private final String chatId;

  /** The relevance score of the best-matching document for this chat. */
  private final double score;
}
