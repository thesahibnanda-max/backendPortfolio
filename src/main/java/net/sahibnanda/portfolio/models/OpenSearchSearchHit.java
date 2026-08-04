package net.sahibnanda.portfolio.models;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Value;

/**
 * A single matched document.
 */
@Value
@Builder
public class OpenSearchSearchHit {

  /**
   * The document's id.
   */
  private String id;

  /**
   * Relevance score, or {@code null} if scoring was disabled.
   */
  private Double score;

  /**
   * The document's fields.
   */
  private Map<String, Object> source;

  /**
   * Highlighted fragments per field, empty if highlighting wasn't requested or
   * no field matched.
   */
  private Map<String, List<String>> highlights;
}
