package net.sahibnanda.portfolio.models;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import net.sahibnanda.portfolio.enums.OpenSearchFieldType;

/**
 * A single field in an OpenSearch document: its name, and optionally the type
 * it's indexed as. A field with no type is stored but not indexed or
 * searchable.
 */
@Value
@Builder
@ToString(of = "fieldName")
@EqualsAndHashCode(of = "fieldName")
public class OpenSearchDocumentField {

  /** The name of the field in the OpenSearch document. */
  @NonNull
  private String fieldName;

  /** The type this field is indexed as, or {@code null} if stored only. */
  private OpenSearchFieldType fieldType;

  /**
   * Whether this field is indexed (searchable), as opposed to stored only.
   *
   * @return {@code true} if this field has a declared type
   */
  public boolean isIndexed() {
    return fieldType != null;
  }

  /**
   * Creates an indexed, searchable field.
   *
   * @param name the field name
   * @param type the type to index the field as
   * @return the new field
   */
  public static OpenSearchDocumentField indexed(final String name,
      final OpenSearchFieldType type) {
    return builder().fieldName(name).fieldType(type).build();
  }

  /**
   * Creates a stored-only field, not indexed or searchable.
   *
   * @param name the field name
   * @return the new field
   */
  public static OpenSearchDocumentField stored(final String name) {
    return builder().fieldName(name).build();
  }
}
