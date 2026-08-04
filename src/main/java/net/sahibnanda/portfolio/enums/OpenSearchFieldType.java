package net.sahibnanda.portfolio.enums;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import lombok.Getter;

/**
 * The OpenSearch field types this application maps documents to, paired with
 * the Java type expected for each field's value.
 */
@Getter
public enum OpenSearchFieldType {

  /** A full-text, analyzed string field. */
  TEXT("text", String.class),
  /** An exact-match, non-analyzed string field. */
  KEYWORD("keyword", String.class),

  /** An 8-bit signed integer field. */
  BYTE("byte", Byte.class),
  /** A 16-bit signed integer field. */
  SHORT("short", Short.class),
  /** A 32-bit signed integer field. */
  INTEGER("integer", Integer.class),
  /** A 64-bit signed integer field. */
  LONG("long", Long.class),
  /** A 64-bit unsigned integer field. */
  UNSIGNED_LONG("unsigned_long", BigInteger.class),
  /** A half-precision (16-bit) floating point field. */
  HALF_FLOAT("half_float", Float.class),
  /** A single-precision (32-bit) floating point field. */
  FLOAT("float", Float.class),
  /** A double-precision (64-bit) floating point field. */
  DOUBLE("double", Double.class),
  /** A floating point field scaled by a fixed factor and stored as a long. */
  SCALED_FLOAT("scaled_float", Double.class),

  /** A boolean field. */
  BOOLEAN("boolean", Boolean.class),

  /** A date/time field. */
  DATE("date", LocalDateTime.class),

  /** A JSON object field. */
  OBJECT("object", Map.class),
  /** A JSON object field, indexed so children are queried independently. */
  NESTED("nested", Map.class),

  /** A base64-encoded binary field. */
  BINARY("binary", byte[].class),
  /** An IPv4 or IPv6 address field. */
  IP("ip", String.class),
  /** A software version field, supporting Semantic Versioning ordering. */
  VERSION("version", String.class),
  /** A pattern field optimized for wildcard/regex queries over long strings. */
  WILDCARD("wildcard", String.class),
  /** A keyword field where every document shares the same field value. */
  CONSTANT_KEYWORD("constant_keyword", String.class);

  /** The OpenSearch type string this constant maps to. */
  private final String value;

  /** The Java type a field of this type's value must be an instance of. */
  private final Class<?> objectClass;

  OpenSearchFieldType(final String openSearchValue,
      final Class<?> fieldObjectClass) {
    this.value = openSearchValue;
    this.objectClass = fieldObjectClass;
  }

  @Override
  public String toString() {
    return value;
  }

  /**
   * Looks up the field type matching an OpenSearch type string.
   *
   * @param value the OpenSearch type string to look up
   * @return the matching field type
   * @throws IllegalArgumentException if no field type matches
   */
  public static OpenSearchFieldType fromValue(final String value) {
    return Arrays.stream(values())
        .filter(type -> type.value.equalsIgnoreCase(value)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "Unknown OpenSearch field type: " + value));
  }
}
