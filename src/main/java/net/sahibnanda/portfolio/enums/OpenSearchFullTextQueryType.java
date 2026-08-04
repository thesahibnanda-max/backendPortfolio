package net.sahibnanda.portfolio.enums;

/**
 * The full-text query kinds
 * {@link net.sahibnanda.portfolio.models.OpenSearchFullTextQueryCondition} can
 * build.
 */
public enum OpenSearchFullTextQueryType {

  /** Analyzed, single-field text match. */
  MATCH,

  /** Analyzed text match across multiple fields. */
  MULTI_MATCH,

  /** Analyzed match requiring terms in the given order. */
  MATCH_PHRASE,

  /** {@code MATCH_PHRASE} with the last term treated as a prefix. */
  MATCH_PHRASE_PREFIX,

  /** Lucene query-syntax search. */
  QUERY_STRING,

  /** Safer, non-throwing variant of {@code QUERY_STRING}. */
  SIMPLE_QUERY_STRING
}
