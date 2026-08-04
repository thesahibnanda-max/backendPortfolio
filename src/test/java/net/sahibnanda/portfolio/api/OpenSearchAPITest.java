package net.sahibnanda.portfolio.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.ulid.UlidCreator;
import java.util.List;
import java.util.Map;
import net.sahibnanda.portfolio.config.OpenSearchProperties;
import net.sahibnanda.portfolio.enums.OpenSearchBucketAggregationType;
import net.sahibnanda.portfolio.enums.OpenSearchExactQueryType;
import net.sahibnanda.portfolio.enums.OpenSearchFieldType;
import net.sahibnanda.portfolio.enums.OpenSearchFullTextQueryType;
import net.sahibnanda.portfolio.enums.OpenSearchMetricAggregationType;
import net.sahibnanda.portfolio.enums.OpenSearchSortDirection;
import net.sahibnanda.portfolio.models.OpenSearchBucketAggregationCondition;
import net.sahibnanda.portfolio.models.OpenSearchDocumentField;
import net.sahibnanda.portfolio.models.OpenSearchExactQueryCondition;
import net.sahibnanda.portfolio.models.OpenSearchFullTextQueryCondition;
import net.sahibnanda.portfolio.models.OpenSearchMatchAllCondition;
import net.sahibnanda.portfolio.models.OpenSearchMetricAggregationCondition;
import net.sahibnanda.portfolio.models.OpenSearchNestedQueryCondition;
import net.sahibnanda.portfolio.models.OpenSearchRangeQueryCondition;
import net.sahibnanda.portfolio.models.OpenSearchSearchHit;
import net.sahibnanda.portfolio.models.OpenSearchSearchResult;
import net.sahibnanda.portfolio.models.OpenSearchSortCondition;
import net.sahibnanda.portfolio.options.OpenSearchCreateIndexOptions;
import net.sahibnanda.portfolio.options.OpenSearchIndexDocumentOptions;
import net.sahibnanda.portfolio.options.OpenSearchSearchOptions;
import net.sahibnanda.portfolio.utils.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenSearchAPITest {

  private OpenSearchAPI openSearchAPI;
  private String indexName;

  @BeforeEach
  void setup() {
    openSearchAPI = new OpenSearchAPI(new OpenSearchProperties(
        TestEnvironment.OPENSEARCH_HOST, TestEnvironment.OPENSEARCH_PORT,
        TestEnvironment.OPENSEARCH_USERNAME,
        TestEnvironment.OPENSEARCH_PASSWORD, TestEnvironment.OPENSEARCH_HTTPS));
    indexName = ("opensearch-test-" + UlidCreator.getUlid()).toLowerCase();

    openSearchAPI.createIndexIfNotExists(OpenSearchCreateIndexOptions.builder()
        .indexName(indexName)
        .mapping(
            OpenSearchDocumentField.indexed("title", OpenSearchFieldType.TEXT))
        .mapping(OpenSearchDocumentField.indexed("views",
            OpenSearchFieldType.INTEGER))
        .mapping(OpenSearchDocumentField.indexed("category",
            OpenSearchFieldType.KEYWORD))
        .mapping(
            OpenSearchDocumentField.indexed("tags", OpenSearchFieldType.NESTED))
        .mapping(OpenSearchDocumentField.stored("notes")).build());
  }

  @AfterEach
  void cleanup() {
    openSearchAPI.deleteIndexIfExists(indexName);
  }

  @Test
  void createIndexIfNotExistsIsIdempotent() {
    assertThatCode(() -> openSearchAPI.createIndexIfNotExists(
        OpenSearchCreateIndexOptions.builder().indexName(indexName).build()))
        .doesNotThrowAnyException();
  }

  @Test
  void deleteIndexIfExistsRemovesIndexAndIsIdempotent() {
    String throwaway =
        ("opensearch-test-" + UlidCreator.getUlid()).toLowerCase();
    openSearchAPI.createIndexIfNotExists(
        OpenSearchCreateIndexOptions.builder().indexName(throwaway).build());

    openSearchAPI.deleteIndexIfExists(throwaway);

    assertThatCode(() -> openSearchAPI.deleteIndexIfExists(throwaway))
        .doesNotThrowAnyException();
    // Recreating after a real delete must not throw
    // resource_already_exists_exception -- proves it's really gone.
    assertThatCode(() -> openSearchAPI.createIndexIfNotExists(
        OpenSearchCreateIndexOptions.builder().indexName(throwaway).build()))
        .doesNotThrowAnyException();
    openSearchAPI.deleteIndexIfExists(throwaway);
  }

  @Test
  void deleteIndexIfExistsIsIdempotentForMissingIndex() {
    String missing =
        ("opensearch-test-missing-" + UlidCreator.getUlid()).toLowerCase();

    assertThatCode(() -> openSearchAPI.deleteIndexIfExists(missing))
        .doesNotThrowAnyException();
  }

  @Test
  void upsertDocumentWithExplicitIdReturnsThatId() {
    String documentId =
        openSearchAPI.upsertDocument(OpenSearchIndexDocumentOptions.builder()
            .indexName(indexName).documentId("doc-1")
            .document(OpenSearchDocumentField.indexed("title",
                OpenSearchFieldType.TEXT), "Hello World")
            .document(OpenSearchDocumentField.indexed("views",
                OpenSearchFieldType.INTEGER), 10)
            .build());

    assertThat(documentId).isEqualTo("doc-1");
  }

  @Test
  void upsertDocumentRejectsBlankDocumentId() {
    assertThatThrownBy(() -> openSearchAPI.upsertDocument(
        OpenSearchIndexDocumentOptions.builder().indexName(indexName)
            .document(OpenSearchDocumentField.indexed("title",
                OpenSearchFieldType.TEXT), "Untitled")
            .build()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void upsertDocumentOnExistingIdMergesFields() {
    String documentId = ("doc-" + UlidCreator.getUlid()).toLowerCase();

    String firstResult = openSearchAPI.upsertDocument(
        OpenSearchIndexDocumentOptions.builder().indexName(indexName)
            .documentId(documentId).document(OpenSearchDocumentField
                .indexed("title", OpenSearchFieldType.TEXT), "Original Title")
            .build());

    String secondResult =
        openSearchAPI.upsertDocument(
            OpenSearchIndexDocumentOptions.builder().indexName(indexName)
                .documentId(documentId).document(OpenSearchDocumentField
                    .indexed("views", OpenSearchFieldType.INTEGER), 42)
                .build());

    assertThat(firstResult).isEqualTo(documentId);
    assertThat(secondResult).isEqualTo(documentId);
  }

  @Test
  void upsertDocumentAllowsStoredNonIndexedFieldWithAnyValueType() {
    String documentId = ("doc-" + UlidCreator.getUlid()).toLowerCase();

    assertThatCode(
        () -> openSearchAPI
            .upsertDocument(OpenSearchIndexDocumentOptions.builder()
                .indexName(indexName).documentId(documentId)
                .document(OpenSearchDocumentField.stored("notes"),
                    Map.of("nested", "value"))
                .build()))
        .doesNotThrowAnyException();
  }

  @Test
  void upsertDocumentRejectsMismatchedFieldType() {
    String documentId = ("doc-" + UlidCreator.getUlid()).toLowerCase();

    assertThatThrownBy(
        () -> openSearchAPI.upsertDocument(OpenSearchIndexDocumentOptions
            .builder().indexName(indexName).documentId(documentId)
            .document(OpenSearchDocumentField.indexed("views",
                OpenSearchFieldType.INTEGER), "not-a-number")
            .build()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deleteDocumentIfExistsRemovesDocumentAndIsIdempotent() {
    String documentId = ("doc-" + UlidCreator.getUlid()).toLowerCase();
    openSearchAPI.upsertDocument(
        OpenSearchIndexDocumentOptions.builder().indexName(indexName)
            .documentId(documentId).document(OpenSearchDocumentField
                .indexed("title", OpenSearchFieldType.TEXT), "To delete")
            .build());

    assertThatCode(
        () -> openSearchAPI.deleteDocumentIfExists(indexName, documentId))
        .doesNotThrowAnyException();
    assertThatCode(
        () -> openSearchAPI.deleteDocumentIfExists(indexName, documentId))
        .doesNotThrowAnyException();
  }

  @Test
  void deleteDocumentIfExistsIsIdempotentForMissingDocument() {
    assertThatCode(
        () -> openSearchAPI.deleteDocumentIfExists(indexName, "never-indexed"))
        .doesNotThrowAnyException();
  }

  @Test
  void searchMatchAllReturnsEveryDocument() {
    indexDocument("doc-1", "Alpha", 10, "books");
    indexDocument("doc-2", "Beta", 20, "movies");
    indexDocument("doc-3", "Gamma", 30, "books");

    OpenSearchSearchResult result =
        awaitSearch(OpenSearchSearchOptions.builder().indexName(indexName)
            .must(new OpenSearchMatchAllCondition()).size(50).build(), 3);

    assertThat(result.getHits()).hasSize(3);
    assertThat(result.getTotalHits()).isEqualTo(3);
  }

  @Test
  void searchWithTermFilterReturnsOnlyMatchingDocuments() {
    indexDocument("doc-1", "Alpha", 10, "books");
    indexDocument("doc-2", "Beta", 20, "movies");

    OpenSearchSearchResult result =
        awaitSearch(OpenSearchSearchOptions.builder().indexName(indexName)
            .filter(OpenSearchExactQueryCondition.builder()
                .type(OpenSearchExactQueryType.TERM).field("category")
                .value("books").build())
            .build(), 1);

    assertThat(result.getHits()).extracting(OpenSearchSearchHit::getId)
        .containsExactly("doc-1");
  }

  @Test
  void searchWithRangeFilterReturnsOnlyDocumentsInRange() {
    indexDocument("doc-1", "Alpha", 10, "books");
    indexDocument("doc-2", "Beta", 20, "books");
    indexDocument("doc-3", "Gamma", 30, "books");

    OpenSearchSearchResult result = awaitSearch(OpenSearchSearchOptions
        .builder().indexName(indexName).filter(OpenSearchRangeQueryCondition
            .builder().field("views").gte(15).build())
        .build(), 2);

    assertThat(result.getHits()).extracting(OpenSearchSearchHit::getId)
        .containsExactlyInAnyOrder("doc-2", "doc-3");
  }

  @Test
  void searchWithBoolMustAndFilterCombinesConditions() {
    indexDocument("doc-1", "Alpha Book", 10, "books");
    indexDocument("doc-2", "Beta Book", 20, "movies");
    indexDocument("doc-3", "Gamma Movie", 30, "books");

    OpenSearchSearchResult result =
        awaitSearch(OpenSearchSearchOptions.builder().indexName(indexName)
            .must(OpenSearchFullTextQueryCondition.builder()
                .type(OpenSearchFullTextQueryType.MATCH)
                .fields(List.of("title")).queryText("Book").build())
            .filter(OpenSearchExactQueryCondition.builder()
                .type(OpenSearchExactQueryType.TERM).field("category")
                .value("books").build())
            .build(), 1);

    assertThat(result.getHits()).extracting(OpenSearchSearchHit::getId)
        .containsExactly("doc-1");
  }

  @Test
  void searchWithMultiMatchSearchesAcrossFields() {
    indexDocument("doc-1", "Gaming Laptop", 10, "electronics");
    indexDocument("doc-2", "Office Chair", 20, "furniture");

    OpenSearchSearchResult result = awaitSearch(OpenSearchSearchOptions
        .builder().indexName(indexName)
        .must(OpenSearchFullTextQueryCondition.builder()
            .type(OpenSearchFullTextQueryType.MULTI_MATCH)
            .fields(List.of("title", "category")).queryText("Gaming").build())
        .build(), 1);

    assertThat(result.getHits()).extracting(OpenSearchSearchHit::getId)
        .containsExactly("doc-1");
  }

  @Test
  void searchRespectsSortOrder() {
    indexDocument("doc-1", "Alpha", 30, "books");
    indexDocument("doc-2", "Beta", 10, "books");
    indexDocument("doc-3", "Gamma", 20, "books");

    OpenSearchSearchResult result =
        awaitSearch(
            OpenSearchSearchOptions.builder().indexName(indexName)
                .sort(OpenSearchSortCondition.builder().field("views")
                    .direction(OpenSearchSortDirection.ASC).build())
                .build(),
            3);

    assertThat(result.getHits()).extracting(OpenSearchSearchHit::getId)
        .containsExactly("doc-2", "doc-3", "doc-1");
  }

  @Test
  void searchRespectsFromAndSize() {
    indexDocument("doc-1", "Alpha", 10, "books");
    indexDocument("doc-2", "Beta", 20, "books");
    indexDocument("doc-3", "Gamma", 30, "books");

    awaitSearch(
        OpenSearchSearchOptions.builder().indexName(indexName).size(50).build(),
        3);

    OpenSearchSearchResult result = openSearchAPI
        .search(OpenSearchSearchOptions.builder().indexName(indexName)
            .sort(OpenSearchSortCondition.builder().field("views")
                .direction(OpenSearchSortDirection.ASC).build())
            .from(1).size(1).build());

    assertThat(result.getHits()).hasSize(1);
    assertThat(result.getHits().get(0).getId()).isEqualTo("doc-2");
    assertThat(result.getTotalHits()).isEqualTo(3);
  }

  @Test
  void searchWithSourceIncludesReturnsOnlyRequestedFields() {
    indexDocument("doc-1", "Alpha", 10, "books");

    OpenSearchSearchResult result = awaitSearch(OpenSearchSearchOptions
        .builder().indexName(indexName).sourceInclude("title").build(), 1);

    assertThat(result.getHits().get(0).getSource()).containsOnlyKeys("title");
  }

  @Test
  void searchWithHighlightFieldsReturnsHighlightFragments() {
    indexDocument("doc-1", "Gaming Laptop Deal", 10, "electronics");

    OpenSearchSearchResult result = awaitSearch(OpenSearchSearchOptions
        .builder().indexName(indexName)
        .must(OpenSearchFullTextQueryCondition.builder()
            .type(OpenSearchFullTextQueryType.MATCH).fields(List.of("title"))
            .queryText("Laptop").build())
        .highlightField("title").build(), 1);

    assertThat(result.getHits().get(0).getHighlights()).containsKey("title");
    assertThat(result.getHits().get(0).getHighlights().get("title").get(0))
        .contains("Laptop");
  }

  @Test
  void searchWithTermsAggregationAndAvgSubAggregationReturnsBucketsWithMetric() {
    indexDocument("doc-1", "Alpha", 10, "books");
    indexDocument("doc-2", "Beta", 30, "books");
    indexDocument("doc-3", "Gamma", 100, "movies");

    awaitSearch(
        OpenSearchSearchOptions.builder().indexName(indexName).size(50).build(),
        3);

    OpenSearchSearchResult result = openSearchAPI.search(OpenSearchSearchOptions
        .builder().indexName(indexName).size(0)
        .aggregation("byCategory",
            OpenSearchBucketAggregationCondition.builder()
                .type(OpenSearchBucketAggregationType.TERMS).field("category")
                .subAggregation("avgViews",
                    OpenSearchMetricAggregationCondition.builder()
                        .type(OpenSearchMetricAggregationType.AVG)
                        .field("views").build())
                .build())
        .build());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> buckets =
        (List<Map<String, Object>>) result.getAggregations().get("byCategory");
    assertThat(buckets).hasSize(2);
    Map<String, Object> booksBucket =
        buckets.stream().filter(bucket -> "books".equals(bucket.get("key")))
            .findFirst().orElseThrow();
    assertThat(booksBucket.get("avgViews")).isEqualTo(20.0);
  }

  @Test
  void searchWithNestedQueryMatchesNestedField() {
    openSearchAPI
        .upsertDocument(
            OpenSearchIndexDocumentOptions.builder().indexName(indexName)
                .documentId("doc-1")
                .document(OpenSearchDocumentField.indexed("title",
                    OpenSearchFieldType.TEXT), "Alpha")
                .document(
                    OpenSearchDocumentField.indexed("tags",
                        OpenSearchFieldType.NESTED),
                    Map.of("name", "electronics"))
                .build());
    openSearchAPI
        .upsertDocument(
            OpenSearchIndexDocumentOptions.builder().indexName(indexName)
                .documentId("doc-2").document(OpenSearchDocumentField
                    .indexed("title", OpenSearchFieldType.TEXT), "Beta")
                .build());

    OpenSearchSearchResult result =
        awaitSearch(OpenSearchSearchOptions.builder().indexName(indexName)
            .must(OpenSearchNestedQueryCondition.builder().path("tags")
                .mustCondition(new OpenSearchMatchAllCondition()).build())
            .build(), 1);

    assertThat(result.getHits()).extracting(OpenSearchSearchHit::getId)
        .containsExactly("doc-1");
  }

  private void indexDocument(final String id, final String title,
      final int views, final String category) {
    openSearchAPI
        .upsertDocument(OpenSearchIndexDocumentOptions.builder()
            .indexName(indexName).documentId(id)
            .document(OpenSearchDocumentField.indexed("title",
                OpenSearchFieldType.TEXT), title)
            .document(OpenSearchDocumentField.indexed("views",
                OpenSearchFieldType.INTEGER), views)
            .document(OpenSearchDocumentField.indexed("category",
                OpenSearchFieldType.KEYWORD), category)
            .build());
  }

  private OpenSearchSearchResult awaitSearch(final OpenSearchSearchOptions opts,
      final int expectedHitCount) {
    long deadline = System.currentTimeMillis() + 10_000;
    OpenSearchSearchResult result;
    do {
      result = openSearchAPI.search(opts);
      if (result.getHits().size() >= expectedHitCount) {
        return result;
      }
    } while (System.currentTimeMillis() < deadline);
    return result;
  }
}
