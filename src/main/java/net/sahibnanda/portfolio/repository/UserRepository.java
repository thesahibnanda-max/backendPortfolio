package net.sahibnanda.portfolio.repository;

import java.util.Optional;
import net.sahibnanda.portfolio.entity.UserEntity;

/** Repository contract for persisting and retrieving user data. */
public interface UserRepository {

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
  UserEntity create(String username, String hashedPassword);

  /**
   * Finds a user by username.
   *
   * @param username the username to look up
   * @return an {@link Optional} containing the user if one exists with the
   *         given username, or an empty {@link Optional} otherwise
   * @throws DatabaseOperationException if the lookup fails
   */
  Optional<UserEntity> findByUsername(String username);

  /**
   * Checks whether a user with the given username exists.
   *
   * @param username the username to check
   * @return {@code true} if a user with the given username exists,
   *         {@code false} otherwise
   * @throws DatabaseOperationException if the check fails
   */
  boolean exists(String username);

  /**
   * Deletes a user.
   *
   * @param username the username of the user to delete
   * @throws UserNotFoundException if no user exists with the given username
   * @throws DatabaseOperationException if the deletion fails
   */
  void delete(String username);

  /**
   * Updates the password of a user.
   *
   * @param username the username of the user to update
   * @param hashedPassword the new hashed password to store
   * @throws UserNotFoundException if no user exists with the given username
   * @throws DatabaseOperationException if the update fails
   */
  void updatePassword(String username, String hashedPassword);
}
