package net.sahibnanda.portfolio.api;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.sahibnanda.portfolio.config.OpenSearchProperties;
import net.sahibnanda.portfolio.enums.OpenSearchQueryOperator;
import net.sahibnanda.portfolio.enums.OpenSearchSortDirection;
import net.sahibnanda.portfolio.exception.OpenSearchOperationException;
import net.sahibnanda.portfolio.models.OpenSearchAggregationCondition;
import net.sahibnanda.portfolio.models.OpenSearchAggregationRange;
import net.sahibnanda.portfolio.models.OpenSearchBucketAggregationCondition;
import net.sahibnanda.portfolio.models.OpenSearchDocumentField;
import net.sahibnanda.portfolio.models.OpenSearchExactQueryCondition;
import net.sahibnanda.portfolio.models.OpenSearchFullTextQueryCondition;
import net.sahibnanda.portfolio.models.OpenSearchMatchAllCondition;
import net.sahibnanda.portfolio.models.OpenSearchMatchNoneCondition;
import net.sahibnanda.portfolio.models.OpenSearchMetricAggregationCondition;
import net.sahibnanda.portfolio.models.OpenSearchNestedQueryCondition;
import net.sahibnanda.portfolio.models.OpenSearchQueryCondition;
import net.sahibnanda.portfolio.models.OpenSearchRangeQueryCondition;
import net.sahibnanda.portfolio.models.OpenSearchSearchHit;
import net.sahibnanda.portfolio.models.OpenSearchSearchResult;
import net.sahibnanda.portfolio.models.OpenSearchSortCondition;
import net.sahibnanda.portfolio.options.OpenSearchCreateIndexOptions;
import net.sahibnanda.portfolio.options.OpenSearchIndexDocumentOptions;
import net.sahibnanda.portfolio.options.OpenSearchSearchOptions;
import net.sahibnanda.portfolio.utils.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationRange;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.CalendarInterval;
import org.opensearch.client.opensearch._types.aggregations.DateHistogramBucket;
import org.opensearch.client.opensearch._types.aggregations.DateRangeExpression;
import org.opensearch.client.opensearch._types.aggregations.HistogramBucket;
import org.opensearch.client.opensearch._types.aggregations.LongTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.MultiBucketBase;
import org.opensearch.client.opensearch._types.aggregations.RangeBucket;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.stereotype.Component;

import javax.print.attribute.standard.ReferenceUriSchemesSupported;

/**
 * Wraps the OpenSearch Java client, configured from
 * {@link OpenSearchProperties}: index, document, and search operations.
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
   * A generified {@code Class} token for {@code Map<String, Object>}, needed
   * because {@code Map<String, Object>.class} isn't valid Java -- this is the
   * standard unchecked-cast idiom for obtaining one. Used as the document type
   * for every search call so no raw {@code Map} type appears anywhere
   * downstream.
   */
  @SuppressWarnings("unchecked")
  private static final Class<Map<String, Object>> DOCUMENT_CLASS =
      (Class<Map<String, Object>>) (Class<?>) Map.class;

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
   * Searches an index.
   *
   * @param opts the index, query conditions, sorting, pagination, source
   *        filtering, highlighting, and aggregations to apply
   * @return the matching documents and any requested aggregation results
   * @throws IllegalArgumentException if the index name is blank, or a condition
   *         is missing a value it requires (e.g. a range query with no bounds)
   * @throws OpenSearchOperationException if the search call fails
   */
  public OpenSearchSearchResult search(final OpenSearchSearchOptions opts) {
    Objects.requireNonNull(opts, "opts must not be null");
    if (StringUtils.isEmpty(opts.getIndexName())) {
      throw new IllegalArgumentException("indexName must not be empty");
    }

    Query rootQuery = buildRootQuery(opts);
    Map<String, Aggregation> aggregations =
        buildAggregations(opts.getAggregations());

    try {
      SearchResponse<Map<String, Object>> response =
          openSearchClient.search(s -> {
            s.index(opts.getIndexName()).query(rootQuery);
            if (opts.getFrom() != null) {
              s.from(opts.getFrom());
            }
            if (opts.getSize() != null) {
              s.size(opts.getSize());
            }
            if (!opts.getSorts().isEmpty()) {
              s.sort(opts.getSorts().stream().map(OpenSearchAPI::buildSort)
                  .toList());
            }
            if (!opts.getSourceIncludes().isEmpty()
                || !opts.getSourceExcludes().isEmpty()) {
              s.source(
                  src -> src.filter(f -> f.includes(opts.getSourceIncludes())
                      .excludes(opts.getSourceExcludes())));
            }
            if (!opts.getHighlightFields().isEmpty()) {
              s.highlight(h -> {
                opts.getHighlightFields()
                    .forEach(field -> h.fields(field, hf -> hf));
                return h;
              });
            }
            if (!aggregations.isEmpty()) {
              s.aggregations(aggregations);
            }
            if (opts.getMinScore() != null) {
              s.minScore(opts.getMinScore());
            }
            if (opts.getTrackTotalHits() != null) {
              s.trackTotalHits(t -> t.enabled(opts.getTrackTotalHits()));
            }
            return s;
          }, DOCUMENT_CLASS);

      return toSearchResult(response);
    } catch (OpenSearchException | IOException e) {
      throw new OpenSearchOperationException(
          "Failed to search index: " + opts.getIndexName(), e);
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

  private static Query buildRootQuery(final OpenSearchSearchOptions opts) {
    List<Query> must = toQueries(opts.getMustConditions());
    List<Query> should = toQueries(opts.getShouldConditions());
    List<Query> mustNot = toQueries(opts.getMustNotConditions());
    List<Query> filter = toQueries(opts.getFilterConditions());

    if (must.isEmpty() && should.isEmpty() && mustNot.isEmpty()
        && filter.isEmpty()) {
      return Query.of(q -> q.matchAll(m -> m));
    }

    return Query.of(q -> q.bool(b -> {
      b.must(must).should(should).mustNot(mustNot).filter(filter);
      if (!StringUtils.isEmpty(opts.getMinimumShouldMatch())) {
        b.minimumShouldMatch(opts.getMinimumShouldMatch());
      }
      return b;
    }));
  }

  private static List<Query> toQueries(
      final List<OpenSearchQueryCondition> conditions) {
    return conditions.stream().map(OpenSearchAPI::buildQuery).toList();
  }

  private static Query buildQuery(final OpenSearchQueryCondition condition) {
    return switch (condition) {
      case OpenSearchMatchAllCondition _ -> Query.of(q -> q.matchAll(m -> m));
      case OpenSearchMatchNoneCondition _ -> Query.of(q -> q.matchNone(m -> m));
      case OpenSearchFullTextQueryCondition c -> buildFullTextQuery(c);
      case OpenSearchExactQueryCondition c -> buildExactQuery(c);
      case OpenSearchRangeQueryCondition c -> buildRangeQuery(c);
      case OpenSearchNestedQueryCondition c -> buildNestedQuery(c);
    };
  }

  private static Query buildFullTextQuery(
      final OpenSearchFullTextQueryCondition c) {
    return switch (c.getType()) {
      case MATCH -> Query.of(q -> q.match(m -> {
        m.field(singleField(c)).query(FieldValue.of(c.getQueryText()));
        ifPresent(c.getBoost(), m::boost);
        ifPresent(c.getFuzziness(), m::fuzziness);
        ifPresent(c.getOperator(),
            operator -> m.operator(toOperator(operator)));
        ifPresent(c.getMinimumShouldMatch(), m::minimumShouldMatch);
        return m;
      }));
      case MATCH_PHRASE -> Query.of(q -> q.matchPhrase(m -> {
        m.field(singleField(c)).query(c.getQueryText());
        ifPresent(c.getBoost(), m::boost);
        ifPresent(c.getSlop(), m::slop);
        return m;
      }));
      case MATCH_PHRASE_PREFIX -> Query.of(q -> q.matchPhrasePrefix(m -> {
        m.field(singleField(c)).query(c.getQueryText());
        ifPresent(c.getBoost(), m::boost);
        ifPresent(c.getSlop(), m::slop);
        ifPresent(c.getMaxExpansions(), m::maxExpansions);
        return m;
      }));
      case MULTI_MATCH -> Query.of(q -> q.multiMatch(m -> {
        m.fields(c.getFields()).query(c.getQueryText());
        ifPresent(c.getBoost(), m::boost);
        ifPresent(c.getFuzziness(), m::fuzziness);
        ifPresent(c.getOperator(),
            operator -> m.operator(toOperator(operator)));
        ifPresent(c.getMinimumShouldMatch(), m::minimumShouldMatch);
        ifPresent(c.getTieBreaker(), m::tieBreaker);
        return m;
      }));
      case QUERY_STRING -> Query.of(q -> q.queryString(m -> {
        m.query(c.getQueryText()).fields(c.getFields());
        ifPresent(c.getBoost(), m::boost);
        ifPresent(c.getMinimumShouldMatch(), m::minimumShouldMatch);
        return m;
      }));
      case SIMPLE_QUERY_STRING -> Query.of(q -> q.simpleQueryString(m -> {
        m.query(c.getQueryText()).fields(c.getFields());
        ifPresent(c.getBoost(), m::boost);
        ifPresent(c.getMinimumShouldMatch(), m::minimumShouldMatch);
        return m;
      }));
    };
  }

  private static <T> void ifPresent(final T value, final Consumer<T> setter) {
    if (value != null) {
      setter.accept(value);
    }
  }

  private static String singleField(final OpenSearchFullTextQueryCondition c) {
    if (c.getFields() == null || c.getFields().size() != 1) {
      throw new IllegalArgumentException(
          c.getType() + " requires exactly one field, got: " + c.getFields());
    }
    return c.getFields().getFirst();
  }

  private static Operator toOperator(final OpenSearchQueryOperator operator) {
    return operator == OpenSearchQueryOperator.AND ? Operator.And : Operator.Or;
  }

  private static Query buildExactQuery(final OpenSearchExactQueryCondition c) {
    return switch (c.getType()) {
      case TERM -> Query.of(q -> q.term(t -> {
        t.field(c.getField()).value(toFieldValue(c.getValue()));
        if (c.getCaseInsensitive() != null) {
          t.caseInsensitive(c.getCaseInsensitive());
        }
        return t;
      }));
      case TERMS -> Query.of(q -> q.terms(t -> t.field(c.getField())
          .terms(tf -> tf.value(toFieldValues(c.getValues())))));
      case IDS ->
        Query.of(q -> q.ids(i -> i.values(toStringList(c.getValues()))));
      case EXISTS -> Query.of(q -> q.exists(e -> e.field(c.getField())));
      case PREFIX -> Query.of(q -> q.prefix(p -> {
        p.field(c.getField()).value(String.valueOf(c.getValue()));
        if (c.getCaseInsensitive() != null) {
          p.caseInsensitive(c.getCaseInsensitive());
        }
        return p;
      }));
      case WILDCARD -> Query.of(q -> q.wildcard(w -> {
        w.field(c.getField()).value(String.valueOf(c.getValue()));
        if (c.getCaseInsensitive() != null) {
          w.caseInsensitive(c.getCaseInsensitive());
        }
        return w;
      }));
      case REGEXP -> Query.of(q -> q.regexp(r -> {
        r.field(c.getField()).value(String.valueOf(c.getValue()));
        if (c.getCaseInsensitive() != null) {
          r.caseInsensitive(c.getCaseInsensitive());
        }
        return r;
      }));
      case FUZZY -> Query.of(q -> q.fuzzy(f -> {
        f.field(c.getField()).value(toFieldValue(c.getValue()));
        if (c.getFuzziness() != null) {
          f.fuzziness(c.getFuzziness());
        }
        return f;
      }));
    };
  }

  private static List<FieldValue> toFieldValues(final List<Object> values) {
    return values.stream().map(OpenSearchAPI::toFieldValue).toList();
  }

  private static List<String> toStringList(final List<Object> values) {
    return values.stream().map(String::valueOf).toList();
  }

  private static FieldValue toFieldValue(final Object value) {
    return switch (value) {
      case String s -> FieldValue.of(s);
      case Boolean b -> FieldValue.of(b);
      case Long l -> FieldValue.of(l);
      case Integer i -> FieldValue.of(i.longValue());
      case Double d -> FieldValue.of(d);
      case Float f -> FieldValue.of(f.doubleValue());
      default -> FieldValue.of(String.valueOf(value));
    };
  }

  private static Query buildRangeQuery(final OpenSearchRangeQueryCondition c) {
    if (c.getGte() == null && c.getGt() == null && c.getLte() == null
        && c.getLt() == null) {
      throw new IllegalArgumentException(
          "range query on '" + c.getField() + "' needs at least one bound");
    }
    return Query.of(q -> q.range(r -> {
      r.field(c.getField());
      if (c.getGte() != null) {
        r.gte(JsonData.of(c.getGte()));
      }
      if (c.getGt() != null) {
        r.gt(JsonData.of(c.getGt()));
      }
      if (c.getLte() != null) {
        r.lte(JsonData.of(c.getLte()));
      }
      if (c.getLt() != null) {
        r.lt(JsonData.of(c.getLt()));
      }
      if (c.getFormat() != null) {
        r.format(c.getFormat());
      }
      if (c.getTimeZone() != null) {
        r.timeZone(c.getTimeZone());
      }
      return r;
    }));
  }

  private static Query buildNestedQuery(
      final OpenSearchNestedQueryCondition c) {
    List<Query> inner = toQueries(c.getMustConditions());
    Query innerQuery = inner.size() == 1 ? inner.getFirst()
        : Query.of(q -> q.bool(b -> b.must(inner)));
    return Query.of(q -> q.nested(n -> n.path(c.getPath()).query(innerQuery)));
  }

  private static SortOptions buildSort(final OpenSearchSortCondition c) {
    SortOrder order =
        c.getDirection() == OpenSearchSortDirection.DESC ? SortOrder.Desc
            : SortOrder.Asc;
    if (StringUtils.isEmpty(c.getField())) {
      return SortOptions.of(s -> s.score(sc -> sc.order(order)));
    }
    return SortOptions
        .of(s -> s.field(f -> f.field(c.getField()).order(order)));
  }

  private static Map<String, Aggregation> buildAggregations(
      final Map<String, OpenSearchAggregationCondition> conditions) {
    return conditions.entrySet().stream().collect(Collectors
        .toMap(Map.Entry::getKey, entry -> buildAggregation(entry.getValue())));
  }

  private static Aggregation buildAggregation(
      final OpenSearchAggregationCondition condition) {
    return switch (condition) {
      case OpenSearchBucketAggregationCondition c -> buildBucketAggregation(c);
      case OpenSearchMetricAggregationCondition c -> buildMetricAggregation(c);
    };
  }

  private static Aggregation buildBucketAggregation(
      final OpenSearchBucketAggregationCondition c) {
    Map<String, Aggregation> subAggregations =
        buildAggregations(c.getSubAggregations());
    return Aggregation.of(a -> {
      var container = switch (c.getType()) {
        case TERMS -> a.terms(t -> {
          t.field(c.getField());
          if (c.getSize() != null) {
            t.size(c.getSize());
          }
          return t;
        });
        case RANGE -> a.range(r -> r.field(c.getField()).ranges(c.getRanges()
            .stream().map(OpenSearchAPI::toAggregationRange).toList()));
        case DATE_RANGE ->
          a.dateRange(r -> r.field(c.getField()).ranges(c.getRanges().stream()
              .map(OpenSearchAPI::toDateRangeExpression).toList()));
        case HISTOGRAM -> a.histogram(
            h -> h.field(c.getField()).interval(c.getHistogramInterval()));
        case DATE_HISTOGRAM -> a.dateHistogram(h -> h.field(c.getField())
            .calendarInterval(toCalendarInterval(c.getCalendarInterval())));
        case FILTERS -> {
          Map<String, Query> namedQueries =
              c.getNamedFilters().entrySet().stream().collect(Collectors.toMap(
                  Map.Entry::getKey, entry -> buildQuery(entry.getValue())));
          yield a.filters(f -> f.filters(b -> b.keyed(namedQueries)));
        }
      };
      if (!subAggregations.isEmpty()) {
        container.aggregations(subAggregations);
      }
      return container;
    });
  }

  private static AggregationRange toAggregationRange(
      final OpenSearchAggregationRange range) {
    return AggregationRange.of(r -> {
      if (range.getKey() != null) {
        r.key(range.getKey());
      }
      if (range.getFrom() != null) {
        r.from(JsonData.of(range.getFrom()));
      }
      if (range.getTo() != null) {
        r.to(JsonData.of(range.getTo()));
      }
      return r;
    });
  }

  private static DateRangeExpression toDateRangeExpression(
      final OpenSearchAggregationRange range) {
    return DateRangeExpression.of(r -> {
      if (range.getKey() != null) {
        r.key(range.getKey());
      }
      if (range.getFrom() != null) {
        r.from(f -> f.expr(String.valueOf(range.getFrom())));
      }
      if (range.getTo() != null) {
        r.to(f -> f.expr(String.valueOf(range.getTo())));
      }
      return r;
    });
  }

  private static CalendarInterval toCalendarInterval(final String interval) {
    String normalized = interval.substring(0, 1).toUpperCase(Locale.ROOT)
        + interval.substring(1).toLowerCase(Locale.ROOT);
    return CalendarInterval.valueOf(normalized);
  }

  private static Aggregation buildMetricAggregation(
      final OpenSearchMetricAggregationCondition c) {
    return switch (c.getType()) {
      case AVG -> Aggregation.of(a -> a.avg(v -> v.field(c.getField())));
      case SUM -> Aggregation.of(a -> a.sum(v -> v.field(c.getField())));
      case MIN -> Aggregation.of(a -> a.min(v -> v.field(c.getField())));
      case MAX -> Aggregation.of(a -> a.max(v -> v.field(c.getField())));
      case STATS -> Aggregation.of(a -> a.stats(v -> v.field(c.getField())));
      case CARDINALITY ->
        Aggregation.of(a -> a.cardinality(v -> v.field(c.getField())));
      case VALUE_COUNT ->
        Aggregation.of(a -> a.valueCount(v -> v.field(c.getField())));
    };
  }

  private static OpenSearchSearchResult toSearchResult(
      final SearchResponse<Map<String, Object>> response) {
    List<OpenSearchSearchHit> hits =
        response.hits().hits().stream().map(OpenSearchAPI::toHit).toList();
    Map<String, Object> aggregationResults =
        response.aggregations() == null ? Map.of()
            : response.aggregations().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                    entry -> toAggregationResult(entry.getValue())));
    long total = response.hits().total() == null ? hits.size()
        : response.hits().total().value();
    return OpenSearchSearchResult.builder().totalHits(total)
        .maxScore(response.maxScore()).hits(hits)
        .aggregations(aggregationResults).build();
  }

  private static OpenSearchSearchHit toHit(final Hit<Map<String, Object>> hit) {
    Map<String, Object> source = hit.source() == null ? Map.of() : hit.source();
    Map<String, List<String>> highlights = hit.highlight();
    return OpenSearchSearchHit.builder().id(hit.id()).score(hit.score())
        .source(source).highlights(highlights).build();
  }

  private static Object toAggregationResult(final Aggregate aggregate) {
    if (aggregate.isSterms()) {
      return toMaps(aggregate.sterms().buckets(), StringTermsBucket::key);
    }
    if (aggregate.isLterms()) {
      return toMaps(aggregate.lterms().buckets(), LongTermsBucket::keyAsString);
    }
    if (aggregate.isDterms()) {
      return toMaps(aggregate.dterms().buckets(),
          bucket -> String.valueOf(bucket.key()));
    }
    if (aggregate.isRange()) {
      return toMaps(aggregate.range().buckets(), RangeBucket::key);
    }
    if (aggregate.isDateRange()) {
      return toMaps(aggregate.dateRange().buckets(), RangeBucket::key);
    }
    if (aggregate.isDateHistogram()) {
      return toMaps(aggregate.dateHistogram().buckets(),
          DateHistogramBucket::keyAsString);
    }
    if (aggregate.isHistogram()) {
      return toMaps(aggregate.histogram().buckets(),
          HistogramBucket::keyAsString);
    }
    if (aggregate.isFilters()) {
      // Always requested as a keyed (named) map, so the array-side key
      // extractor is never invoked here.
      return toMaps(aggregate.filters().buckets(), null);
    }
    if (aggregate.isAvg()) {
      return aggregate.avg().value();
    }
    if (aggregate.isSum()) {
      return aggregate.sum().value();
    }
    if (aggregate.isMin()) {
      return aggregate.min().value();
    }
    if (aggregate.isMax()) {
      return aggregate.max().value();
    }
    if (aggregate.isValueCount()) {
      return aggregate.valueCount().value();
    }
    if (aggregate.isCardinality()) {
      return aggregate.cardinality().value();
    }
    if (aggregate.isStats()) {
      var stats = aggregate.stats();
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("count", stats.count());
      result.put("min", stats.min());
      result.put("max", stats.max());
      result.put("avg", stats.avg());
      result.put("sum", stats.sum());
      return result;
    }
    throw new OpenSearchOperationException(
        "Unsupported aggregation result kind: " + aggregate._kind());
  }

  private static <T extends MultiBucketBase> List<Map<String, Object>> toMaps(
      final Buckets<T> buckets, final Function<T, String> keyFn) {
    if (buckets.isKeyed()) {
      return buckets.keyed().entrySet().stream()
          .map(entry -> bucketToMap(entry.getKey(), entry.getValue())).toList();
    }
    return buckets.array().stream()
        .map(bucket -> bucketToMap(keyFn.apply(bucket), bucket)).toList();
  }

  private static Map<String, Object> bucketToMap(final String key,
      final MultiBucketBase bucket) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("key", key);
    result.put("docCount", bucket.docCount());
    bucket.aggregations().forEach((name, subAggregate) -> result.put(name,
        toAggregationResult(subAggregate)));
    return result;
  }
}
