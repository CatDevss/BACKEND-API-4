package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.Dictionary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictionaryRepository extends JpaRepository<Dictionary, Long> {
}
