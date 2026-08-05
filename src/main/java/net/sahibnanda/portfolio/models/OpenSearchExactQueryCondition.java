package net.sahibnanda.portfolio.models;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import net.sahibnanda.portfolio.enums.OpenSearchExactQueryType;

/**
 * An exact-match query condition. Not every field applies to every
 * {@link #type} -- {@link net.sahibnanda.portfolio.api.OpenSearchAPI} reads
 * only the fields relevant to the chosen type; the rest are ignored.
 */
@Value
@Builder
public class OpenSearchExactQueryCondition implements OpenSearchQueryCondition {

  /** Which exact-match query kind this condition builds. */
  private OpenSearchExactQueryType type;

  /**
   * The field to match against. Required for every type except {@code IDS},
   * which matches on the document id across the whole index.
   */
  private String field;

  /**
   * The value to match -- {@code TERM}/{@code PREFIX}/{@code WILDCARD}/
   * {@code REGEXP}/{@code FUZZY} only.
   */
  private Object value;

  /** The values to match -- {@code TERMS}/{@code IDS} only. */
  private List<Object> values;

  /**
   * Whether matching ignores case -- {@code TERM}/{@code WILDCARD}/
   * {@code REGEXP} only.
   */
  private Boolean caseInsensitive;

  /** Fuzziness, e.g. {@code "AUTO"} or {@code "2"} -- {@code FUZZY} only. */
  private String fuzziness;

  /**
   * Relevance boost for this condition, or {@code null} for the default --
   * {@code TERM} only.
   */
  private Float boost;
}
