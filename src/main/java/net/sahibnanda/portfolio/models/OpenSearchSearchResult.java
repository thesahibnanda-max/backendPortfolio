package net.sahibnanda.portfolio.models;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Value;

/**
 * The result of an {@link net.sahibnanda.portfolio.api.OpenSearchAPI#search}
 * call.
 */
@Value
@Builder
public class OpenSearchSearchResult {

  /**
   * Total number of matching documents.
   */
  private long totalHits;

  /**
   * Highest relevance score among the returned hits, or {@code null}.
   */
  private Double maxScore;

  /**
   * The matched documents for this page.
   */
  private List<OpenSearchSearchHit> hits;

  /**
   * Requested aggregation results, keyed by the name given in
   * {@code OpenSearchSearchOptions.aggregations}. Bucket aggregations map to
   * {@code List<Map<String, Object>>} (one map per bucket: {@code key},
   * {@code docCount}, plus any sub-aggregation results under their own names);
   * metric aggregations map to their raw numeric value, except {@code stats},
   * which maps to a small map of {@code count}/{@code min}/
   * {@code max}/{@code avg}/{@code sum}.
   */
  private Map<String, Object> aggregations;
}
