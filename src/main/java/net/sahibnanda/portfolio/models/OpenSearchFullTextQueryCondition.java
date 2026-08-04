package net.sahibnanda.portfolio.models;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import net.sahibnanda.portfolio.enums.OpenSearchFullTextQueryType;
import net.sahibnanda.portfolio.enums.OpenSearchQueryOperator;

/**
 * A full-text query condition. Not every field applies to every {@link #type}
 * -- {@link net.sahibnanda.portfolio.api.OpenSearchAPI} reads only the fields
 * relevant to the chosen type; the rest are ignored.
 */
@Value
@Builder
public class OpenSearchFullTextQueryCondition
    implements OpenSearchQueryCondition {

  /** Which full-text query kind this condition builds. */
  private OpenSearchFullTextQueryType type;

  /**
   * The field(s) to search. {@code MATCH}/{@code MATCH_PHRASE}/
   * {@code MATCH_PHRASE_PREFIX} use only the first field; the rest use every
   * field given.
   */
  private List<String> fields;

  /**
   * The text (or, for {@code QUERY_STRING}/{@code SIMPLE_QUERY_STRING}, the
   * query syntax) to search for.
   */
  private String queryText;

  /** Relevance boost for this condition, or {@code null} for the default. */
  private Float boost;

  /**
   * Fuzziness, e.g. {@code "AUTO"} or {@code "2"} -- {@code MATCH}/
   * {@code MULTI_MATCH} only.
   */
  private String fuzziness;

  /**
   * Whether every term must match ({@code AND}) or any ({@code OR}) --
   * {@code MATCH}/{@code MULTI_MATCH} only, defaults to OpenSearch's default
   * ({@code OR}) if {@code null}.
   */
  private OpenSearchQueryOperator operator;

  /** Minimum-should-match spec, e.g. {@code "75%"} -- where supported. */
  private String minimumShouldMatch;

  /** Term proximity slop -- {@code MATCH_PHRASE} only. */
  private Integer slop;

  /** Max prefix expansions -- {@code MATCH_PHRASE_PREFIX} only. */
  private Integer maxExpansions;

  /** Score-combination tiebreaker -- {@code MULTI_MATCH} only. */
  private Float tieBreaker;
}
