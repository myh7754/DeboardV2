package org.example.deboardv2.user.repository;

import org.example.deboardv2.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    User getReferenceByNickname(String nickname);

    Optional<User> findByNickname(String nickname);
}
