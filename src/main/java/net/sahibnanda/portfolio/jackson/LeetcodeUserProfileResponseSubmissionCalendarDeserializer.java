package net.sahibnanda.portfolio.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.sahibnanda.portfolio.utils.JsonUtils;

public final class LeetcodeUserProfileResponseSubmissionCalendarDeserializer
    extends JsonDeserializer<Map<Long, Integer>> {

  /** Deserializes the submission calendar JSON into a long-keyed map. */
  @Override
  public Map<Long, Integer> deserialize(final JsonParser parser,
      final DeserializationContext context) throws IOException {

    String json = parser.getValueAsString();

    if (json == null || json.isBlank()) {
      return Map.of();
    }

    Map<String, Integer> stringKeyMap =
        JsonUtils.fromJson(json, new TypeReference<HashMap<String, Integer>>() {
        });

    return stringKeyMap.entrySet().stream().collect(Collectors.toMap(
        entry -> entry.getKey() == null ? 0L : Long.parseLong(entry.getKey()),
        entry -> Objects.requireNonNullElse(entry.getValue(), 0)));
  }
}
