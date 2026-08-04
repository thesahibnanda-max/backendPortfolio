package net.sahibnanda.portfolio.models;

import lombok.Builder;
import lombok.Value;

/**
 * A range query condition. At least one of {@link #gte}/{@link #gt}/
 * {@link #lte}/{@link #lt} must be set --
 * {@link net.sahibnanda.portfolio.api.OpenSearchAPI} validates this.
 */
@Value
@Builder
public class OpenSearchRangeQueryCondition implements OpenSearchQueryCondition {

  /** The field to range-check. */
  private String field;

  /** Inclusive lower bound, or {@code null} for unbounded. */
  private Object gte;

  /** Exclusive lower bound, or {@code null} for unbounded. */
  private Object gt;

  /** Inclusive upper bound, or {@code null} for unbounded. */
  private Object lte;

  /** Exclusive upper bound, or {@code null} for unbounded. */
  private Object lt;

  /**
   * Date format pattern for the bounds, or {@code null} for the field's mapped
   * format.
   */
  private String format;

  /** Time zone to interpret date bounds in, or {@code null} for UTC. */
  private String timeZone;
}
