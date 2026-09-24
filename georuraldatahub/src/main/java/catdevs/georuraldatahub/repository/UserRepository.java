package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}