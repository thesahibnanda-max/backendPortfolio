package net.sahibnanda.portfolio.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link net.sahibnanda.portfolio.services.SearchService}'s
 * chat search index.
 *
 * @param chatIndexName the OpenSearch index chat documents are stored in (must
 *        be lowercase -- OpenSearch rejects any other index name)
 * @param chatNumberOfShards the number of primary shards for the index
 * @param chatNumberOfReplicas the number of replica shards for the index
 */
@ConfigurationProperties(prefix = "search-service")
public record SearchProperties(String chatIndexName, Integer chatNumberOfShards,
    Integer chatNumberOfReplicas) {

  /** Validates that no component of the properties is null. */
  public SearchProperties {
    Objects.requireNonNull(chatIndexName, "chatIndexName must not be null");
    Objects.requireNonNull(chatNumberOfShards,
        "chatNumberOfShards must not be null");
    Objects.requireNonNull(chatNumberOfReplicas,
        "chatNumberOfReplicas must not be null");
  }
}
