package net.sahibnanda.portfolio.repository.jooq;

import java.time.LocalDateTime;
import java.util.Optional;
import net.sahibnanda.portfolio.entity.UserEntity;
import net.sahibnanda.portfolio.exception.DatabaseOperationException;
import net.sahibnanda.portfolio.exception.DuplicateUsernameException;
import net.sahibnanda.portfolio.exception.UserNotFoundException;
import net.sahibnanda.portfolio.jooq.Tables;
import net.sahibnanda.portfolio.jooq.tables.records.UsersRecord;
import net.sahibnanda.portfolio.repository.UserRepository;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** jOOQ-backed implementation of {@link UserRepository}. */
@Repository
@SuppressWarnings("java:S8688")
public class JooqUserRepository implements UserRepository {

  /** jOOQ context used to execute user queries. */
  private final DSLContext dslContext;

  /**
   * Creates a new user repository.
   *
   * @param jooqDslContext the jOOQ context used to execute queries
   */
  public JooqUserRepository(final DSLContext jooqDslContext) {
    this.dslContext = jooqDslContext;
  }

  /**
   * Creates and persists a new user.
   *
   * @param username the username of the new user
   * @param hashedPassword the hashed password to store for the user
   * @return the created user entity
   * @throws DuplicateUsernameException if a user with the given username
   *         already exists
   * @throws DatabaseOperationException if the user cannot be persisted
   */
  @Override
  public UserEntity create(final String username, final String hashedPassword) {
    LocalDateTime createdAt = LocalDateTime.now();
    try {
      dslContext.insertInto(Tables.USERS).set(Tables.USERS.USERNAME, username)
          .set(Tables.USERS.PASSWORD_HASH, hashedPassword)
          .set(Tables.USERS.CREATED_AT, createdAt).execute();
    } catch (DuplicateKeyException _) {
      throw new DuplicateUsernameException(username);
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException("Failed to create user: " + username,
          e);
    }
    return new UserEntity(username, hashedPassword, createdAt);
  }

  /**
   * Finds a user by username.
   *
   * @param username the username to look up
   * @return an {@link Optional} containing the user if one exists with the
   *         given username, or an empty {@link Optional} otherwise
   * @throws DatabaseOperationException if the lookup fails
   */
  @Override
  public Optional<UserEntity> findByUsername(final String username) {
    try {
      return dslContext.selectFrom(Tables.USERS)
          .where(Tables.USERS.USERNAME.eq(username))
          .fetchOptional(this::toEntity);
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException("Failed to find user: " + username,
          e);
    }
  }

  /**
   * Checks whether a user with the given username exists.
   *
   * @param username the username to check
   * @return {@code true} if a user with the given username exists,
   *         {@code false} otherwise
   * @throws DatabaseOperationException if the check fails
   */
  @Override
  public boolean exists(final String username) {
    try {
      return dslContext.fetchExists(dslContext.selectFrom(Tables.USERS)
          .where(Tables.USERS.USERNAME.eq(username)));
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException(
          "Failed to check user existence: " + username, e);
    }
  }

  /**
   * Deletes a user.
   *
   * @param username the username of the user to delete
   * @throws UserNotFoundException if no user exists with the given username
   * @throws DatabaseOperationException if the deletion fails
   */
  @Override
  public void delete(final String username) {
    int deleted;
    try {
      deleted = dslContext.deleteFrom(Tables.USERS)
          .where(Tables.USERS.USERNAME.eq(username)).execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException("Failed to delete user: " + username,
          e);
    }
    if (deleted == 0) {
      throw new UserNotFoundException(username);
    }
  }

  /**
   * Updates the password of a user.
   *
   * @param username the username of the user to update
   * @param hashedPassword the new hashed password to store
   * @throws UserNotFoundException if no user exists with the given username
   * @throws DatabaseOperationException if the update fails
   */
  @Override
  public void updatePassword(final String username,
      final String hashedPassword) {
    int updated;
    try {
      updated = dslContext.update(Tables.USERS)
          .set(Tables.USERS.PASSWORD_HASH, hashedPassword)
          .where(Tables.USERS.USERNAME.eq(username)).execute();
    } catch (org.jooq.exception.DataAccessException | DataAccessException e) {
      throw new DatabaseOperationException(
          "Failed to update password for user: " + username, e);
    }
    if (updated == 0) {
      throw new UserNotFoundException(username);
    }
  }

  private UserEntity toEntity(final UsersRecord userRecord) {
    return new UserEntity(userRecord.getUsername(),
        userRecord.getPasswordHash(), userRecord.getCreatedAt());
  }
}
