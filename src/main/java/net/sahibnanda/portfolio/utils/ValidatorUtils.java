package net.sahibnanda.portfolio.utils;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.experimental.UtilityClass;
import net.sahibnanda.portfolio.exception.InputTooLongException;
import net.sahibnanda.portfolio.exception.InvalidEmailException;
import net.sahibnanda.portfolio.exception.InvalidPasswordException;

/** Validation for email addresses, password strength, and chat input length. */
@UtilityClass
public final class ValidatorUtils {

  /** The minimum allowed password length. */
  private static final int MIN_PASSWORD_LENGTH = 8;

  /** The maximum allowed password length. */
  private static final int MAX_PASSWORD_LENGTH = 32;

  /** The highest ASCII code point; anything above this is rejected. */
  private static final int MAX_ASCII_CODE_POINT = 127;

  /**
   * Validates and normalizes an email address (lowercased, trimmed).
   *
   * @param email the email address to validate
   * @return the normalized email address
   * @throws InvalidEmailException if {@code email} is not a valid address
   */
  public String validateAndNormalizeEmail(final String email) {
    try {
      InternetAddress address = new InternetAddress(email.toLowerCase().trim());
      address.validate();
      return address.getAddress();
    } catch (AddressException e) {
      throw new InvalidEmailException("Invalid email address: " + email, e);
    }
  }

  /**
   * Validates password strength: 8-32 characters, at least one uppercase
   * letter, one lowercase letter, one ASCII special character, and no non-ASCII
   * characters (blocks emoji).
   *
   * @param password the password to validate
   * @throws InvalidPasswordException if any rule fails
   */
  public void validatePassword(final String password) {
    if (StringUtils.isEmpty(password)) {
      throw new InvalidPasswordException("Password must not be empty.");
    }
    if (password.length() < MIN_PASSWORD_LENGTH
        || password.length() > MAX_PASSWORD_LENGTH) {
      throw new InvalidPasswordException(
          "Password must be between " + MIN_PASSWORD_LENGTH + " and "
              + MAX_PASSWORD_LENGTH + " characters.");
    }
    if (password.chars().anyMatch(c -> c > MAX_ASCII_CODE_POINT)) {
      throw new InvalidPasswordException(
          "Password must not contain non-ASCII characters (e.g. emoji).");
    }
    if (password.chars().noneMatch(Character::isUpperCase)) {
      throw new InvalidPasswordException(
          "Password must contain at least one uppercase letter.");
    }
    if (password.chars().noneMatch(Character::isLowerCase)) {
      throw new InvalidPasswordException(
          "Password must contain at least one lowercase letter.");
    }
    if (password.chars().noneMatch(ValidatorUtils::isAsciiSpecialCharacter)) {
      throw new InvalidPasswordException(
          "Password must contain at least one special character.");
    }
  }

  private static boolean isAsciiSpecialCharacter(final int codePoint) {
    return codePoint <= MAX_ASCII_CODE_POINT
        && !Character.isLetterOrDigit(codePoint);
  }

  /**
   * Validates that {@code value} does not exceed {@code maxLength} characters.
   * A null value passes -- blank/required checks are the caller's job.
   *
   * @param value the value to check
   * @param fieldName the name of the field, used in the thrown exception
   * @param maxLength the maximum allowed length
   * @throws InputTooLongException if value exceeds maxLength
   */
  public void validateMaxLength(final String value, final String fieldName,
      final int maxLength) {
    if (value != null && value.length() > maxLength) {
      throw new InputTooLongException(fieldName, value.length(), maxLength);
    }
  }
}
