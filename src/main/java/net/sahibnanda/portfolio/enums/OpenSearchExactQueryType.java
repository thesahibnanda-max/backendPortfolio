package net.sahibnanda.portfolio.enums;

/**
 * The exact-match query kinds
 * {@link net.sahibnanda.portfolio.models.OpenSearchExactQueryCondition} can
 * build.
 */
public enum OpenSearchExactQueryType {

  /** Exact match on a single value. */
  TERM,

  /** Exact match on any of several values. */
  TERMS,

  /** Match by document id. */
  IDS,

  /** Whether a field is present and non-null. */
  EXISTS,

  /** Field starts with a given prefix. */
  PREFIX,

  /** Field matches a wildcard pattern. */
  WILDCARD,

  /** Field matches a regular expression. */
  REGEXP,

  /** Exact match tolerant of typos. */
  FUZZY
}
