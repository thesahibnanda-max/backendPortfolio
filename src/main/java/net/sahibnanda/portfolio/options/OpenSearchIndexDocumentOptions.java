package net.sahibnanda.portfolio.options;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import net.sahibnanda.portfolio.models.OpenSearchDocumentField;

/**
 * Options for upserting a document into OpenSearch: the target index, the
 * document id, and the field values to set.
 */
@Data
@Builder
public class OpenSearchIndexDocumentOptions {

  /** The name of the index to write the document into. */
  private String indexName;

  /** The id to upsert the document under. */
  private String documentId;

  /** The field values making up the document. */
  @Singular("document")
  private Map<OpenSearchDocumentField, Object> document;
}
