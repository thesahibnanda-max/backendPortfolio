package net.sahibnanda.portfolio.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.sahibnanda.portfolio.exception.InvalidEmailException;
import net.sahibnanda.portfolio.exception.InvalidPasswordException;
import org.junit.jupiter.api.Test;

class ValidatorUtilsTest {

  @Test
  void validatePasswordAcceptsAStrongPassword() {
    ValidatorUtils.validatePassword("Str0ng!Pass");
  }

  @Test
  void validatePasswordRejectsTooShort() {
    assertThatThrownBy(() -> ValidatorUtils.validatePassword("Sh0rt!"))
        .isInstanceOf(InvalidPasswordException.class);
  }

  @Test
  void validatePasswordRejectsTooLong() {
    String tooLong = "Aa1!".repeat(10);
    assertThatThrownBy(() -> ValidatorUtils.validatePassword(tooLong))
        .isInstanceOf(InvalidPasswordException.class);
  }

  @Test
  void validatePasswordRejectsMissingUppercase() {
    assertThatThrownBy(() -> ValidatorUtils.validatePassword("weak1!pass"))
        .isInstanceOf(InvalidPasswordException.class);
  }

  @Test
  void validatePasswordRejectsMissingLowercase() {
    assertThatThrownBy(() -> ValidatorUtils.validatePassword("WEAK1!PASS"))
        .isInstanceOf(InvalidPasswordException.class);
  }

  @Test
  void validatePasswordRejectsMissingSpecialCharacter() {
    assertThatThrownBy(() -> ValidatorUtils.validatePassword("Weak1Pass"))
        .isInstanceOf(InvalidPasswordException.class);
  }

  @Test
  void validatePasswordRejectsNonAsciiCharacters() {
    assertThatThrownBy(() -> ValidatorUtils.validatePassword("Str0ng!😀"))
        .isInstanceOf(InvalidPasswordException.class);
  }

  @Test
  void validatePasswordRejectsBlank() {
    assertThatThrownBy(() -> ValidatorUtils.validatePassword("  "))
        .isInstanceOf(InvalidPasswordException.class);
  }

  @Test
  void validateAndNormalizeEmailLowercasesAndTrims() {
    String normalized =
        ValidatorUtils.validateAndNormalizeEmail("  Foo@Example.COM  ");

    assertThat(normalized).isEqualTo("foo@example.com");
  }

  @Test
  void validateAndNormalizeEmailRejectsMalformedAddress() {
    assertThatThrownBy(
        () -> ValidatorUtils.validateAndNormalizeEmail("not-an-email"))
        .isInstanceOf(InvalidEmailException.class);
  }
}
