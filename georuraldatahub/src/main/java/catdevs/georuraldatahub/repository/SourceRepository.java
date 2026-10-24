package catdevs.georuraldatahub.repository;

import catdevs.georuraldatahub.entity.Source;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceRepository extends JpaRepository<Source, Long> {

    @Query("""
        SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
        FROM Source s
        WHERE LOWER(s.name) = LOWER(:name)
        """)
    boolean existsByName(@Param("name") String name);
}
