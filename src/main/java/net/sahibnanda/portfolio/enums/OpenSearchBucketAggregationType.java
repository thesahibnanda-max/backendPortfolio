package net.sahibnanda.portfolio.enums;

/**
 * The bucket aggregation kinds
 * {@link net.sahibnanda.portfolio.models.OpenSearchBucketAggregationCondition}
 * can build.
 */
public enum OpenSearchBucketAggregationType {

  /** One bucket per distinct field value. */
  TERMS,

  /** One bucket per explicit numeric range. */
  RANGE,

  /** One bucket per explicit date range. */
  DATE_RANGE,

  /** One bucket per fixed-width numeric interval. */
  HISTOGRAM,

  /** One bucket per calendar/fixed date interval. */
  DATE_HISTOGRAM,

  /** One bucket per named filter query. */
  FILTERS
}
