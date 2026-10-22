
package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.entity.Quarantine;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuarantineService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<Quarantine> findByStatus(String status) {

        return entityManager.createQuery(
                """
                SELECT q
                FROM Quarantine q
                WHERE q.status = :status
                """,
                Quarantine.class)
                .setParameter("status", status)
                .getResultList();
    }
}
