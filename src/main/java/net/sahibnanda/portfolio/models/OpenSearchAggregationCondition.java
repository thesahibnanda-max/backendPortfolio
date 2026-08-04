package net.sahibnanda.portfolio.models;

/**
 * A single OpenSearch aggregation, keyed by name in
 * {@code OpenSearchSearchOptions.aggregations}.
 */
public sealed interface OpenSearchAggregationCondition permits
    OpenSearchBucketAggregationCondition, OpenSearchMetricAggregationCondition {
}
