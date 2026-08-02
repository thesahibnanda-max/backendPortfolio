package net.sahibnanda.portfolio.repository;

import java.util.Optional;
import net.sahibnanda.portfolio.entity.UserEntity;

public interface UserRepository {

  UserEntity create(String username, String hashedPassword);

  Optional<UserEntity> findByUsername(String username);

  boolean exists(String username);

  void delete(String username);

  void updatePassword(String username, String hashedPassword);
}
