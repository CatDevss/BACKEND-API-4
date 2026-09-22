package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, Long> {

    boolean existsByName(String name);
}