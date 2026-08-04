package net.sahibnanda.portfolio.api;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.config.OpenSearchProperties;
import net.sahibnanda.portfolio.exception.OpenSearchOperationException;
import net.sahibnanda.portfolio.models.OpenSearchDocumentField;
import net.sahibnanda.portfolio.options.OpenSearchCreateIndexOptions;
import net.sahibnanda.portfolio.options.OpenSearchIndexDocumentOptions;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.stereotype.Component;

import javax.print.attribute.standard.ReferenceUriSchemesSupported;

/**
 * Wraps the OpenSearch Java client, configured from
 * {@link OpenSearchProperties}: index and document lifecycle operations.
 */
@Slf4j
@Component
public final class OpenSearchAPI {

  /**
   * Error type OpenSearch returns creating an index that exists.
   */
  private static final String RESOURCE_ALREADY_EXISTS_ERROR_TYPE =
      "resource_already_exists_exception";

  /**
   * Error type OpenSearch returns for a missing index.
   */
  private static final String INDEX_NOT_FOUND_ERROR_TYPE =
      "index_not_found_exception";

  /**
   * Scaling factor applied to {@code scaled_float} mapped fields.
   */
  private static final double SCALED_FLOAT_SCALING_FACTOR = 100.0;

  /**
   * The configured OpenSearch client.
   */
  private final OpenSearchClient openSearchClient;

  /**
   * The underlying transport, closed on shutdown.
   */
  private final OpenSearchTransport transport;

  /**
   * Creates a new OpenSearch API wrapper.
   *
   * @param openSearchProperties connection settings for the OpenSearch cluster
   */
  public OpenSearchAPI(final OpenSearchProperties openSearchProperties) {
    Objects.requireNonNull(openSearchProperties,
        "openSearchProperties must not be null");

    HttpHost httpHost = new HttpHost(
        Boolean.TRUE.equals(openSearchProperties.https())
            ? ReferenceUriSchemesSupported.HTTPS.toString()
            : ReferenceUriSchemesSupported.HTTP.toString(),
        openSearchProperties.host(), openSearchProperties.port());

    BasicCredentialsProvider credentialsProvider =
        new BasicCredentialsProvider();
    credentialsProvider.setCredentials(new AuthScope(httpHost),
        new UsernamePasswordCredentials(openSearchProperties.username(),
            openSearchProperties.password().toCharArray()));

    this.transport = ApacheHttpClient5TransportBuilder.builder(httpHost)
        .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
            .setDefaultCredentialsProvider(credentialsProvider))
        .build();
    this.openSearchClient = new OpenSearchClient(transport);
  }

  /**
   * Creates an index if it doesn't already exist.
   *
   * @param opts the index name, shard/replica counts, and field mappings
   * @throws IllegalArgumentException if the index name is blank
   * @throws OpenSearchOperationException if the create call fails
   */
  public void createIndexIfNotExists(final OpenSearchCreateIndexOptions opts) {
    Objects.requireNonNull(opts, "opts must not be null");
    if (StringUtils.isEmpty(opts.getIndexName())) {
      throw new IllegalArgumentException("indexName must not be empty");
    }

    CreateIndexRequest.Builder builder =
        CreateIndexRequest.builder().index(opts.getIndexName());
    applySettings(builder, opts);
    applyMappings(builder, opts);

    try {
      openSearchClient.indices().create(builder.build());
      log.debug("Created OpenSearch index {}", opts.getIndexName());
    } catch (OpenSearchException e) {
      if (!isErrorType(e, RESOURCE_ALREADY_EXISTS_ERROR_TYPE)) {
        throw new OpenSearchOperationException(
            "Failed to create OpenSearch index: " + opts.getIndexName(), e);
      }
    } catch (IOException e) {
      throw new OpenSearchOperationException(
          "Failed to create OpenSearch index: " + opts.getIndexName(), e);
    }
  }

  /**
   * Deletes an index if it exists.
   *
   * @param indexName the index to delete
   * @throws IllegalArgumentException if the index name is blank
   * @throws OpenSearchOperationException if the delete call fails
   */
  public void deleteIndexIfExists(final String indexName) {
    if (StringUtils.isEmpty(indexName)) {
      throw new IllegalArgumentException("indexName must not be empty");
    }
    try {
      openSearchClient.indices().delete(d -> d.index(indexName));
      log.debug("Deleted OpenSearch index {}", indexName);
    } catch (OpenSearchException e) {
      if (!isErrorType(e, INDEX_NOT_FOUND_ERROR_TYPE)) {
        throw new OpenSearchOperationException(
            "Failed to delete OpenSearch index: " + indexName, e);
      }
    } catch (IOException e) {
      throw new OpenSearchOperationException(
          "Failed to delete OpenSearch index: " + indexName, e);
    }
  }

  /**
   * Upserts a document: creates it if {@code opts.getDocumentId()} doesn't
   * already exist in the index, or merges the given fields into the existing
   * document if it does (OpenSearch's native partial-update semantics -- fields
   * not present in {@code opts.getDocument()} are left untouched on an existing
   * document).
   *
   * @param opts the target index, document id, and field values to set
   * @return the id of the upserted document
   * @throws IllegalArgumentException if the index name or document id is blank,
   *         the document is empty, or a field's value doesn't match its
   *         declared type
   * @throws OpenSearchOperationException if the upsert call fails
   */
  public String upsertDocument(final OpenSearchIndexDocumentOptions opts) {
    Objects.requireNonNull(opts, "opts must not be null");
    if (StringUtils.isEmpty(opts.getIndexName())) {
      throw new IllegalArgumentException("indexName must not be empty");
    }
    if (StringUtils.isEmpty(opts.getDocumentId())) {
      throw new IllegalArgumentException("documentId must not be empty");
    }
    Map<OpenSearchDocumentField, Object> document = opts.getDocument();
    if (document == null || document.isEmpty()) {
      throw new IllegalArgumentException("document must not be empty");
    }
    validateFieldTypes(document);

    Map<String, Object> source = document.entrySet().stream().collect(Collectors
        .toMap(entry -> entry.getKey().getFieldName(), Map.Entry::getValue));
    String documentId = opts.getDocumentId();

    try {
      var result = openSearchClient.update(i -> i.index(opts.getIndexName())
          .doc(source).id(documentId).docAsUpsert(Boolean.TRUE), Void.class);
      log.debug("Upserted document {} into {}", result.id(),
          opts.getIndexName());
      return result.id();
    } catch (OpenSearchException | IOException e) {
      throw new OpenSearchOperationException(
          "Failed to upsert document into: " + opts.getIndexName(), e);
    }
  }


  /**
   * Deletes a document if it exists. OpenSearch reports a missing document as a
   * normal response (not an exception), so this is naturally idempotent.
   *
   * @param indexName the index the document belongs to
   * @param documentId the id of the document to delete
   * @throws IllegalArgumentException if either argument is blank
   * @throws OpenSearchOperationException if the delete call fails
   */
  public void deleteDocumentIfExists(final String indexName,
      final String documentId) {
    if (StringUtils.isEmpty(indexName)) {
      throw new IllegalArgumentException("indexName must not be empty");
    }
    if (StringUtils.isEmpty(documentId)) {
      throw new IllegalArgumentException("documentId must not be empty");
    }
    try {
      openSearchClient.delete(d -> d.index(indexName).id(documentId));
      log.debug("Deleted document {} from {}", documentId, indexName);
    } catch (OpenSearchException | IOException e) {
      throw new OpenSearchOperationException(
          "Failed to delete document " + documentId + " from: " + indexName, e);
    }
  }

  /**
   * Closes the underlying transport on shutdown.
   */
  @PreDestroy
  void close() {
    try {
      transport.close();
    } catch (IOException e) {
      log.warn("Failed to close OpenSearch transport cleanly", e);
    }
  }

  private static void applySettings(final CreateIndexRequest.Builder builder,
      final OpenSearchCreateIndexOptions opts) {
    IndexSettings.Builder settings = IndexSettings.builder();
    boolean hasSettings = false;

    if (opts.getNumberOfShards() != null && opts.getNumberOfShards() > 0) {
      settings.numberOfShards(opts.getNumberOfShards());
      hasSettings = true;
    }
    if (opts.getNumberOfReplicas() != null && opts.getNumberOfReplicas() >= 0) {
      settings.numberOfReplicas(opts.getNumberOfReplicas());
      hasSettings = true;
    }
    if (hasSettings) {
      builder.settings(settings.build());
    }
  }

  private static void applyMappings(final CreateIndexRequest.Builder builder,
      final OpenSearchCreateIndexOptions opts) {
    var mappings = opts.getMappings();
    if (mappings == null || mappings.isEmpty()) {
      return;
    }
    TypeMapping.Builder mappingBuilder =
        TypeMapping.builder().dynamic(DynamicMapping.False);
    for (OpenSearchDocumentField field : mappings) {
      if (!field.isIndexed()) {
        continue;
      }
      mappingBuilder.properties(field.getFieldName(),
          p -> switch (field.getFieldType()) {
            case TEXT -> p.text(t -> t);
            case KEYWORD -> p.keyword(k -> k);
            case BYTE -> p.byte_(b -> b);
            case SHORT -> p.short_(s -> s);
            case INTEGER -> p.integer(i -> i);
            case LONG -> p.long_(l -> l);
            case UNSIGNED_LONG -> p.unsignedLong(u -> u);
            case HALF_FLOAT -> p.halfFloat(h -> h);
            case FLOAT -> p.float_(f -> f);
            case DOUBLE -> p.double_(d -> d);
            case SCALED_FLOAT -> p.scaledFloat(
                sf -> sf.scalingFactor(SCALED_FLOAT_SCALING_FACTOR));
            case BOOLEAN -> p.boolean_(b -> b);
            case DATE -> p.date(d -> d);
            case OBJECT -> p.object(o -> o);
            case NESTED -> p.nested(n -> n);
            case BINARY -> p.binary(b -> b);
            case IP -> p.ip(ip -> ip);
            case VERSION -> p.version(v -> v);
            case WILDCARD -> p.wildcard(w -> w);
            case CONSTANT_KEYWORD -> p.constantKeyword(c -> c);
          });
    }
    builder.mappings(mappingBuilder.build());
  }

  private static void validateFieldTypes(
      final Map<OpenSearchDocumentField, Object> document) {
    document.forEach((field, value) -> {
      if (value == null || !field.isIndexed()) {
        return;
      }
      Class<?> expected = field.getFieldType().getObjectClass();
      if (!expected.isInstance(value)) {
        throw new IllegalArgumentException(String.format(
            "Field '%s' expects %s but got %s", field.getFieldName(),
            expected.getSimpleName(), value.getClass().getSimpleName()));
      }
    });
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private static boolean isErrorType(final OpenSearchException e,
      final String errorType) {
    return e.error() != null && errorType.equals(e.error().type());
  }
}
