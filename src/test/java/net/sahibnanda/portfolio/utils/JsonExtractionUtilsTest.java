package net.sahibnanda.portfolio.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import net.sahibnanda.portfolio.exception.JsonExtractionException;
import org.junit.jupiter.api.Test;

class JsonExtractionUtilsTest {

  @Builder
  @Jacksonized
  @Data
  static class SamplePayload {
    private String requiredContexts;
    private String reason;
  }

  @Test
  void extractsDirectJson() {
    String raw =
        "{\"requiredContexts\":\"LEETCODE\",\"reason\":\"coding profile\"}";

    SamplePayload payload =
        JsonExtractionUtils.extractJson(raw, SamplePayload.class);

    assertThat(payload.getRequiredContexts()).isEqualTo("LEETCODE");
    assertThat(payload.getReason()).isEqualTo("coding profile");
  }

  @Test
  void extractsFromJsonFencedCodeBlock() {
    String raw = "Sure, here you go:\n```json\n"
        + "{\"requiredContexts\":\"PROFILE\",\"reason\":\"backend projects\"}\n```";

    SamplePayload payload =
        JsonExtractionUtils.extractJson(raw, SamplePayload.class);

    assertThat(payload.getRequiredContexts()).isEqualTo("PROFILE");
  }

  @Test
  void extractsFromPlainFencedCodeBlock() {
    String raw =
        "```\n{\"requiredContexts\":\"GITHUB\",\"reason\":\"repos\"}\n```";

    SamplePayload payload =
        JsonExtractionUtils.extractJson(raw, SamplePayload.class);

    assertThat(payload.getRequiredContexts()).isEqualTo("GITHUB");
  }

  @Test
  void extractsFromSingleBacktickFence() {
    String raw =
        "`{\"requiredContexts\":\"NONE\",\"reason\":\"general question\"}`";

    SamplePayload payload =
        JsonExtractionUtils.extractJson(raw, SamplePayload.class);

    assertThat(payload.getRequiredContexts()).isEqualTo("NONE");
  }

  @Test
  void recoversRealObjectAfterDecoyBraceViaBraceMatching() {
    String raw = "The format looks like {example} but here is the real answer: "
        + "{\"requiredContexts\":\"CODEFORCES\",\"reason\":\"competitive profile\"}";

    SamplePayload payload =
        JsonExtractionUtils.extractJson(raw, SamplePayload.class);

    assertThat(payload.getRequiredContexts()).isEqualTo("CODEFORCES");
  }

  @Test
  void recoversFromTrailingComma() {
    String raw =
        "{\"requiredContexts\":\"PERSONALITY\",\"reason\":\"hobbies\",}";

    SamplePayload payload =
        JsonExtractionUtils.extractJson(raw, SamplePayload.class);

    assertThat(payload.getRequiredContexts()).isEqualTo("PERSONALITY");
  }

  @Test
  void throwsOnBlankInput() {
    assertThatThrownBy(
        () -> JsonExtractionUtils.extractJson("   ", SamplePayload.class))
        .isInstanceOf(JsonExtractionException.class);
    assertThatThrownBy(
        () -> JsonExtractionUtils.extractJson(null, SamplePayload.class))
        .isInstanceOf(JsonExtractionException.class);
  }

  @Test
  void throwsOnNonJsonGarbage() {
    assertThatThrownBy(() -> JsonExtractionUtils
        .extractJson("I cannot help with that.", SamplePayload.class))
        .isInstanceOf(JsonExtractionException.class);
  }
}
