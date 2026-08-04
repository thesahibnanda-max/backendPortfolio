package net.sahibnanda.portfolio.models;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import net.sahibnanda.portfolio.enums.OpenSearchBucketAggregationType;

/**
 * A bucket aggregation. Not every field applies to every {@link #type} -- see
 * each field's doc for which type(s) use it.
 */
@Value
@Builder
public class OpenSearchBucketAggregationCondition
    implements OpenSearchAggregationCondition {

  /** Which bucket aggregation kind this builds. */
  private OpenSearchBucketAggregationType type;

  /** The field to bucket on -- every type except {@code FILTERS}. */
  private String field;

  /** Max number of buckets -- {@code TERMS} only. */
  private Integer size;

  /** Explicit bucket boundaries -- {@code RANGE}/{@code DATE_RANGE} only. */
  @Singular
  private List<OpenSearchAggregationRange> ranges;

  /** Fixed numeric bucket width -- {@code HISTOGRAM} only. */
  private Double histogramInterval;

  /**
   * Calendar interval, e.g. {@code "day"}/{@code "month"} --
   * {@code DATE_HISTOGRAM} only.
   */
  private String calendarInterval;

  /** Named filter queries, one bucket per entry -- {@code FILTERS} only. */
  @Singular
  private Map<String, OpenSearchQueryCondition> namedFilters;

  /** Nested aggregations computed within each bucket, keyed by name. */
  @Singular
  private Map<String, OpenSearchAggregationCondition> subAggregations;
}
