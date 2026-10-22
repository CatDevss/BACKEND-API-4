package catdevs.georuraldatahub.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import catdevs.georuraldatahub.entity.Dictionary;

public interface DictionaryRepository extends JpaRepository<Dictionary, Long> {

    Optional<Dictionary> findFirstBySource_IdOrderByVersionDesc(Long sourceId);
}
