package net.sahibnanda.portfolio.utils;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.experimental.UtilityClass;
import net.sahibnanda.portfolio.exception.TokenException;

/**
 * Reversible symmetric encryption (AES/GCM) for auth tokens -- unlike password
 * hashing, the plain text must be recoverable, so this is real encryption, not
 * a hash.
 */
@UtilityClass
public final class TokenUtils {

  /** The cipher transformation used for encryption/decryption. */
  private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

  /** The algorithm the derived key is used with. */
  private static final String KEY_ALGORITHM = "AES";

  /** The digest algorithm used to derive the AES key from a passphrase. */
  private static final String KEY_DIGEST_ALGORITHM = "SHA-256";

  /** The length, in bytes, of the random GCM initialization vector. */
  private static final int GCM_IV_LENGTH_BYTES = 12;

  /** The length, in bits, of the GCM authentication tag. */
  private static final int GCM_TAG_LENGTH_BITS = 128;

  /**
   * Encrypts plain text into a URL-safe token.
   *
   * @param plainText the text to encrypt
   * @param secretKey the passphrase to encrypt with
   * @return the encrypted, URL-safe token
   * @throws TokenException if encryption fails
   */
  public String encrypt(final String plainText, final String secretKey) {
    try {
      byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
      new SecureRandom().nextBytes(iv);

      Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, deriveKey(secretKey),
          new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] cipherText =
          cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
    } catch (GeneralSecurityException e) {
      throw new TokenException("Failed to encrypt token.", e);
    }
  }

  /**
   * Decrypts a token produced by {@link #encrypt(String, String)}.
   *
   * @param token the encrypted token
   * @param secretKey the passphrase it was encrypted with
   * @return the original plain text
   * @throws TokenException if decryption fails (including a mismatched
   *         {@code secretKey} or a tampered/malformed token)
   */
  public String decrypt(final String token, final String secretKey) {
    try {
      byte[] combined = Base64.getUrlDecoder().decode(token);
      byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
      byte[] cipherText =
          Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

      Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, deriveKey(secretKey),
          new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new TokenException("Failed to decrypt token.", e);
    }
  }

  private static SecretKeySpec deriveKey(final String secretKey) {
    try {
      byte[] hashed = MessageDigest.getInstance(KEY_DIGEST_ALGORITHM)
          .digest(secretKey.getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(hashed, KEY_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(KEY_DIGEST_ALGORITHM + " not available.",
          e);
    }
  }
}
