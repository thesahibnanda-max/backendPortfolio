package net.sahibnanda.portfolio.repository.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import net.sahibnanda.portfolio.entity.UserEntity;
import net.sahibnanda.portfolio.exception.DuplicateUsernameException;
import net.sahibnanda.portfolio.exception.UserNotFoundException;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqUserRepositoryTest extends AbstractRepositoryIntegrationTest {

  @Autowired private JooqUserRepository userRepository;

  @Test
  void createPersistsUserWithCreationTimestamp() {
    UserEntity created = userRepository.create("alice", "hashed-pw");

    assertThat(created.username()).isEqualTo("alice");
    assertThat(created.passwordHash()).isEqualTo("hashed-pw");
    assertThat(created.createdAt()).isNotNull();
  }

  @Test
  void createRejectsDuplicateUsername() {
    userRepository.create("alice", "hashed-pw");

    assertThatThrownBy(() -> userRepository.create("alice", "other-hash"))
        .isInstanceOf(DuplicateUsernameException.class);
  }

  @Test
  void findByUsernameReturnsEmptyWhenAbsent() {
    Optional<UserEntity> found = userRepository.findByUsername("missing");

    assertThat(found).isEmpty();
  }

  @Test
  void findByUsernameReturnsUserWhenPresent() {
    userRepository.create("alice", "hashed-pw");

    Optional<UserEntity> found = userRepository.findByUsername("alice");

    assertThat(found).isPresent();
    assertThat(found.get().username()).isEqualTo("alice");
  }

  @Test
  void existsReflectsPresence() {
    assertThat(userRepository.exists("alice")).isFalse();

    userRepository.create("alice", "hashed-pw");

    assertThat(userRepository.exists("alice")).isTrue();
  }

  @Test
  void deleteRemovesUser() {
    userRepository.create("alice", "hashed-pw");

    userRepository.delete("alice");

    assertThat(userRepository.exists("alice")).isFalse();
  }

  @Test
  void deleteThrowsWhenUserMissing() {
    assertThatThrownBy(() -> userRepository.delete("missing"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void updatePasswordChangesHash() {
    userRepository.create("alice", "old-hash");

    userRepository.updatePassword("alice", "new-hash");

    assertThat(userRepository.findByUsername("alice").orElseThrow().passwordHash())
        .isEqualTo("new-hash");
  }

  @Test
  void updatePasswordThrowsWhenUserMissing() {
    assertThatThrownBy(() -> userRepository.updatePassword("missing", "new-hash"))
        .isInstanceOf(UserNotFoundException.class);
  }
}
