package net.sahibnanda.portfolio.repository.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Optional;
import net.sahibnanda.portfolio.entity.UserEntity;
import net.sahibnanda.portfolio.exception.DuplicateUsernameException;
import net.sahibnanda.portfolio.exception.UserNotFoundException;
import net.sahibnanda.portfolio.repository.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryFixtureTest extends AbstractRepositoryIntegrationTest {

  @Autowired
  private JooqUserRepository userRepository;

  @BeforeEach
  void loadFixtures() {
    loadFixtureScript("fixtures/repository-test-data.sql");
  }

  @Test
  void findByUsernameReturnsSeededUser() {
    Optional<UserEntity> found = userRepository.findByUsername("alice");

    assertThat(found).isPresent();
    assertThat(found.get().username()).isEqualTo("alice");
    assertThat(found.get().passwordHash()).isEqualTo("hashed-pw-alice");
    assertThat(found.get().createdAt())
        .isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
  }

  @Test
  void findByUsernameReturnsEmptyForUnknownUser() {
    assertThat(userRepository.findByUsername("nobody")).isEmpty();
  }

  @Test
  void existsReflectsSeededUsers() {
    assertThat(userRepository.exists("bob")).isTrue();
    assertThat(userRepository.exists("nobody")).isFalse();
  }

  @Test
  void createAddsToSeededUsers() {
    UserEntity created = userRepository.create("dave", "hashed-pw-dave");

    assertThat(created.username()).isEqualTo("dave");
    assertThat(userRepository.exists("dave")).isTrue();
    assertThat(userRepository.exists("alice")).isTrue();
  }

  @Test
  void createRejectsDuplicateOfSeededUsername() {
    assertThatThrownBy(() -> userRepository.create("alice", "new-hash"))
        .isInstanceOf(DuplicateUsernameException.class);
  }

  @Test
  void updatePasswordChangesSeededUsersHash() {
    userRepository.updatePassword("carol", "new-hash-carol");

    assertThat(
        userRepository.findByUsername("carol").orElseThrow().passwordHash())
        .isEqualTo("new-hash-carol");
  }

  @Test
  void updatePasswordThrowsForUnknownUser() {
    assertThatThrownBy(
        () -> userRepository.updatePassword("nobody", "new-hash"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void deleteRemovesSeededUser() {
    userRepository.delete("bob");

    assertThat(userRepository.exists("bob")).isFalse();
    assertThat(userRepository.exists("alice")).isTrue();
  }

  @Test
  void deleteThrowsForUnknownUser() {
    assertThatThrownBy(() -> userRepository.delete("nobody"))
        .isInstanceOf(UserNotFoundException.class);
  }
}
