package net.sahibnanda.portfolio.models;

import lombok.Builder;
import lombok.Value;
import net.sahibnanda.portfolio.enums.OpenSearchSortDirection;

/**
 * A single sort clause. A {@code null} {@link #field} sorts by relevance score
 * instead of a document field.
 */
@Value
@Builder
public class OpenSearchSortCondition {

  /** The field to sort by, or {@code null} to sort by relevance score. */
  private String field;

  /** Sort direction. */
  private OpenSearchSortDirection direction;
}
