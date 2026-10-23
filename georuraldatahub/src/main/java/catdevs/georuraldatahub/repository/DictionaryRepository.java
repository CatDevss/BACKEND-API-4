package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.Dictionary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DictionaryRepository extends JpaRepository<Dictionary, Long> {
    Optional<Dictionary> findByIdAndSourceId(Long id, Long sourceId);
    boolean existsBySourceId(Long sourceId);
    List<Dictionary> findBySourceId(Long sourceId);
}
