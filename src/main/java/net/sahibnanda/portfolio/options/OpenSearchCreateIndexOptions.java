package net.sahibnanda.portfolio.options;

import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import net.sahibnanda.portfolio.models.OpenSearchDocumentField;

/**
 * Options for creating an OpenSearch index: its name, shard/replica counts, and
 * field mappings.
 */
@Data
@Builder
public class OpenSearchCreateIndexOptions {

  /** The name of the index to create. */
  private String indexName;

  /** The number of primary shards, or {@code null} for the cluster default. */
  private Integer numberOfShards;

  /** The number of replica shards, or {@code null} for the cluster default. */
  private Integer numberOfReplicas;

  /** The fields to map on the new index. */
  @Singular("mapping")
  private Set<OpenSearchDocumentField> mappings;
}
