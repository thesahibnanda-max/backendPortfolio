package net.sahibnanda.portfolio.models;

/** Matches every document, unconditionally. */
public record OpenSearchMatchAllCondition()
    implements
      OpenSearchQueryCondition {
}
