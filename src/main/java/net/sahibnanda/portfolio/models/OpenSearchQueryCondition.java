package net.sahibnanda.portfolio.models;

/**
 * A single OpenSearch query condition. Each implementation corresponds to one
 * supported query kind; {@link net.sahibnanda.portfolio.api.OpenSearchAPI}
 * dispatches on the concrete type via an exhaustive pattern-matching switch.
 */
public sealed interface OpenSearchQueryCondition
    permits OpenSearchMatchAllCondition, OpenSearchMatchNoneCondition,
    OpenSearchFullTextQueryCondition, OpenSearchExactQueryCondition,
    OpenSearchRangeQueryCondition, OpenSearchNestedQueryCondition {
}
