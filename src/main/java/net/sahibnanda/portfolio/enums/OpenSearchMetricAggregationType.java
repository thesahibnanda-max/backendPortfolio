package net.sahibnanda.portfolio.enums;

/**
 * The metric aggregation kinds
 * {@link net.sahibnanda.portfolio.models.OpenSearchMetricAggregationCondition}
 * can build.
 */
public enum OpenSearchMetricAggregationType {

  /** Average of a numeric field. */
  AVG,

  /** Sum of a numeric field. */
  SUM,

  /** Minimum of a numeric field. */
  MIN,

  /** Maximum of a numeric field. */
  MAX,

  /** Count/min/max/avg/sum of a numeric field in one pass. */
  STATS,

  /** Approximate count of distinct values. */
  CARDINALITY,

  /** Count of non-null values. */
  VALUE_COUNT
}
