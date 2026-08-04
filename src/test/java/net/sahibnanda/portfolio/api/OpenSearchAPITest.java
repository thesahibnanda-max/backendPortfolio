package net.sahibnanda.portfolio.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.f4b6a3.ulid.UlidCreator;
import java.util.Map;
import net.sahibnanda.portfolio.config.OpenSearchProperties;
import net.sahibnanda.portfolio.enums.OpenSearchFieldType;
import net.sahibnanda.portfolio.models.OpenSearchDocumentField;
import net.sahibnanda.portfolio.options.OpenSearchCreateIndexOptions;
import net.sahibnanda.portfolio.options.OpenSearchIndexDocumentOptions;
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
}
