package net.sahibnanda.portfolio.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.sahibnanda.portfolio.exception.TokenException;
import org.junit.jupiter.api.Test;

class TokenUtilsTest {

  @Test
  void decryptRecoversTheOriginalPlainText() {
    String token = TokenUtils.encrypt("alice", "secret");

    assertThat(TokenUtils.decrypt(token, "secret")).isEqualTo("alice");
  }

  @Test
  void encryptingTwiceProducesDifferentTokensThatBothDecryptCorrectly() {
    String tokenOne = TokenUtils.encrypt("alice", "secret");
    String tokenTwo = TokenUtils.encrypt("alice", "secret");

    assertThat(tokenOne).isNotEqualTo(tokenTwo);
    assertThat(TokenUtils.decrypt(tokenOne, "secret")).isEqualTo("alice");
    assertThat(TokenUtils.decrypt(tokenTwo, "secret")).isEqualTo("alice");
  }

  @Test
  void decryptingWithTheWrongSecretKeyThrows() {
    String token = TokenUtils.encrypt("alice", "secret");

    assertThatThrownBy(() -> TokenUtils.decrypt(token, "wrong-secret"))
        .isInstanceOf(TokenException.class);
  }

  @Test
  void decryptingGarbageInputThrows() {
    assertThatThrownBy(() -> TokenUtils.decrypt("not-a-real-token", "secret"))
        .isInstanceOf(TokenException.class);
  }
}
