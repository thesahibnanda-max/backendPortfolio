package net.sahibnanda.portfolio.models;

import lombok.Builder;
import lombok.Value;

/** A single bucket boundary for a range/date-range aggregation. */
@Value
@Builder
public class OpenSearchAggregationRange {

  /**
   * Label for this bucket in the response, or {@code null} for the default
   * OpenSearch-generated key.
   */
  private String key;

  /** Inclusive lower bound, or {@code null} for unbounded. */
  private Object from;

  /** Exclusive upper bound, or {@code null} for unbounded. */
  private Object to;
}
