package net.sahibnanda.portfolio.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.UncheckedIOException;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public final class JsonUtils {

  /** Shared Jackson mapper configured for ISO-8601 dates. */
  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  /**
   * Deserializes the given JSON string into an instance of clazz.
   *
   * @param <T> the target type
   * @param json the JSON string to parse
   * @param clazz the target class
   * @return the deserialized object
   */
  public <T> T fromJson(final String json, final Class<T> clazz) {
    try {
      return objectMapper.readValue(json, clazz);
    } catch (JsonProcessingException e) {
      log.error("Failed to deserialize JSON into {}", clazz, e);
      throw new UncheckedIOException("Failed to deserialize JSON", e);
    }
  }

  /**
   * Deserializes the given JSON string using a type reference.
   *
   * @param <T> the target type
   * @param json the JSON string to parse
   * @param typeReference the target type reference
   * @return the deserialized object
   */
  public <T> T fromJson(final String json,
      final TypeReference<T> typeReference) {
    try {
      return objectMapper.readValue(json, typeReference);
    } catch (JsonProcessingException e) {
      log.error("Failed to deserialize JSON into {}", typeReference, e);
      throw new UncheckedIOException("Failed to deserialize JSON", e);
    }
  }

  /**
   * Attempts to deserialize the given JSON string into an instance of clazz,
   * returning empty instead of throwing or logging on failure -- for callers
   * that try multiple candidate strings and only care about the first one that
   * parses (e.g. {@link JsonExtractionUtils}).
   *
   * @param <T> the target type
   * @param json the JSON string to parse
   * @param clazz the target class
   * @return the deserialized object, or empty if it could not be parsed
   */
  public <T> Optional<T> tryFromJson(final String json, final Class<T> clazz) {
    try {
      return Optional.ofNullable(objectMapper.readValue(json, clazz));
    } catch (JsonProcessingException | RuntimeException e) {
      return Optional.empty();
    }
  }

  /**
   * Serializes the given object into its JSON string form.
   *
   * @param value the object to serialize
   * @return the JSON string representation
   */
  public String toJson(final Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize object of type {}",
          value == null ? null : value.getClass(), e);
      throw new UncheckedIOException("Failed to serialize object", e);
    }
  }
}
