package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.CollectionExecution; 
import org.springframework.data.jpa.repository.JpaRepository; 
import java.util.List;

public interface CollectionExecutionRepository extends JpaRepository<CollectionExecution,Long>{ 
    boolean existsBySourceIdAndHashAndStatus(Long sourceId,String hash,String status);
     List<CollectionExecution> findBySourceIdOrderByStartedAtDesc(Long sourceId); 
}
