package net.sahibnanda.portfolio.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContextTypeTest {

  @Test
  void hasExactlySixValues() {
    assertThat(ContextType.values()).hasSize(6);
  }

  @Test
  void everyValueHasANonBlankDescription() {
    for (ContextType contextType : ContextType.values()) {
      assertThat(contextType.getDescription()).isNotBlank();
    }
  }

  @Test
  void noneIsPresent() {
    assertThat(ContextType.valueOf("NONE")).isEqualTo(ContextType.NONE);
  }
}
