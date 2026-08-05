package net.sahibnanda.portfolio.options;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import net.sahibnanda.portfolio.models.OpenSearchAggregationCondition;
import net.sahibnanda.portfolio.models.OpenSearchQueryCondition;
import net.sahibnanda.portfolio.models.OpenSearchSortCondition;

/**
 * Options for an OpenSearch search: query conditions (combined the same way
 * OpenSearch's own bool query combines {@code must}/{@code should}/
 * {@code must_not}/{@code filter}), sorting, pagination, source filtering,
 * highlighting, and aggregations.
 */
@Data
@Builder
public class OpenSearchSearchOptions {

  /** The index to search. */
  private String indexName;

  /** Conditions every matching document must satisfy (scored). */
  @Singular("must")
  private List<OpenSearchQueryCondition> mustConditions;

  /** Conditions that boost relevance when matched, not required. */
  @Singular("should")
  private List<OpenSearchQueryCondition> shouldConditions;

  /** Conditions no matching document may satisfy. */
  @Singular("mustNot")
  private List<OpenSearchQueryCondition> mustNotConditions;

  /** Conditions every matching document must satisfy (not scored). */
  @Singular("filter")
  private List<OpenSearchQueryCondition> filterConditions;

  /** Minimum-should-match spec applied to {@link #shouldConditions}. */
  private String minimumShouldMatch;

  /** Sort order; empty sorts by relevance score, descending. */
  @Singular("sort")
  private List<OpenSearchSortCondition> sorts;

  /** Result offset, or {@code null} for the default (0). */
  private Integer from;

  /** Page size, or {@code null} for the default (10). */
  private Integer size;

  /** Fields to include in each hit's source, empty for all fields. */
  @Singular("sourceInclude")
  private List<String> sourceIncludes;

  /** Fields to exclude from each hit's source. */
  @Singular("sourceExclude")
  private List<String> sourceExcludes;

  /**
   * Fields to highlight matches in, using OpenSearch's default highlighter
   * settings.
   */
  @Singular("highlightField")
  private List<String> highlightFields;

  /** Aggregations to compute, keyed by result name. */
  @Singular("aggregation")
  private Map<String, OpenSearchAggregationCondition> aggregations;

  /** Discard hits scoring below this, or {@code null} for no minimum. */
  private Double minScore;

  /**
   * Whether to compute the exact total hit count, or {@code null} for
   * OpenSearch's default.
   */
  private Boolean trackTotalHits;

  /**
   * Field to collapse results on (one hit per value, highest-scoring first), or
   * {@code null} for no collapsing.
   */
  private String collapseField;

  /**
   * Whether to gather term/document frequency statistics across every shard
   * before scoring ({@code dfs_query_then_fetch}), instead of the default
   * per-shard statistics ({@code query_then_fetch}). Per-shard statistics can
   * rank two otherwise-identical documents very differently depending on which
   * shard they land on -- enable this when relative ranking (e.g. relevance
   * boosts between document types) needs to be reliable, at the cost of an
   * extra round trip. {@code null} uses OpenSearch's default
   * ({@code query_then_fetch}).
   */
  private Boolean distributedTermFrequency;
}
