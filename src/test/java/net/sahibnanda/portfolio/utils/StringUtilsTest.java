package net.sahibnanda.portfolio.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringUtilsTest {

  @Test
  void generateUlidProducesTwentySixCharacterUppercaseValue() {
    String ulid = StringUtils.generateUlid();

    assertThat(ulid).hasSize(26);
    assertThat(StringUtils.isValidUlid(ulid)).isTrue();
  }

  @Test
  void generateUlidProducesUniqueValues() {
    String first = StringUtils.generateUlid();
    String second = StringUtils.generateUlid();

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void isValidUlidRejectsNullEmptyAndMalformedInput() {
    assertThat(StringUtils.isValidUlid(null)).isFalse();
    assertThat(StringUtils.isValidUlid("")).isFalse();
    assertThat(StringUtils.isValidUlid("not-a-ulid")).isFalse();
  }
}
