package net.sahibnanda.portfolio.models;

import lombok.Builder;
import lombok.Value;
import net.sahibnanda.portfolio.enums.OpenSearchMetricAggregationType;

/** A metric aggregation over a single field. */
@Value
@Builder
public class OpenSearchMetricAggregationCondition
    implements OpenSearchAggregationCondition {

  /** Which metric this computes. */
  private OpenSearchMetricAggregationType type;

  /** The field to compute the metric over. */
  private String field;
}
