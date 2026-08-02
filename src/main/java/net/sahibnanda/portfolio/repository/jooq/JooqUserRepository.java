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

@Repository
public class JooqUserRepository implements UserRepository {

  private final DSLContext dslContext;

  public JooqUserRepository(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  @Override
  public UserEntity create(String username, String hashedPassword) {
    LocalDateTime createdAt = LocalDateTime.now();
    try {
      dslContext
          .insertInto(Tables.USERS)
          .set(Tables.USERS.USERNAME, username)
          .set(Tables.USERS.PASSWORD_HASH, hashedPassword)
          .set(Tables.USERS.CREATED_AT, createdAt)
          .execute();
    } catch (DuplicateKeyException e) {
      throw new DuplicateUsernameException(username);
    } catch (org.jooq.exception.DataAccessException e) {
      throw new DatabaseOperationException("Failed to create user: " + username, e);
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to create user: " + username, e);
    }
    return new UserEntity(username, hashedPassword, createdAt);
  }

  @Override
  public Optional<UserEntity> findByUsername(String username) {
    try {
      return dslContext
          .selectFrom(Tables.USERS)
          .where(Tables.USERS.USERNAME.eq(username))
          .fetchOptional(this::toEntity);
    } catch (org.jooq.exception.DataAccessException e) {
      throw new DatabaseOperationException("Failed to find user: " + username, e);
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to find user: " + username, e);
    }
  }

  @Override
  public boolean exists(String username) {
    try {
      return dslContext.fetchExists(
          dslContext.selectFrom(Tables.USERS).where(Tables.USERS.USERNAME.eq(username)));
    } catch (org.jooq.exception.DataAccessException e) {
      throw new DatabaseOperationException("Failed to check user existence: " + username, e);
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to check user existence: " + username, e);
    }
  }

  @Override
  public void delete(String username) {
    int deleted;
    try {
      deleted =
          dslContext.deleteFrom(Tables.USERS).where(Tables.USERS.USERNAME.eq(username)).execute();
    } catch (org.jooq.exception.DataAccessException e) {
      throw new DatabaseOperationException("Failed to delete user: " + username, e);
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to delete user: " + username, e);
    }
    if (deleted == 0) {
      throw new UserNotFoundException(username);
    }
  }

  @Override
  public void updatePassword(String username, String hashedPassword) {
    int updated;
    try {
      updated =
          dslContext
              .update(Tables.USERS)
              .set(Tables.USERS.PASSWORD_HASH, hashedPassword)
              .where(Tables.USERS.USERNAME.eq(username))
              .execute();
    } catch (org.jooq.exception.DataAccessException e) {
      throw new DatabaseOperationException("Failed to update password for user: " + username, e);
    } catch (DataAccessException e) {
      throw new DatabaseOperationException("Failed to update password for user: " + username, e);
    }
    if (updated == 0) {
      throw new UserNotFoundException(username);
    }
  }

  private UserEntity toEntity(UsersRecord record) {
    return new UserEntity(record.getUsername(), record.getPasswordHash(), record.getCreatedAt());
  }
}
