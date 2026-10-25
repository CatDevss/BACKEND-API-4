package catdevs.georuraldatahub.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import catdevs.georuraldatahub.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);
}