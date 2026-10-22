package catdevs.georuraldatahub.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.entity.Dictionary;

@Service
public class DictionaryService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public Dictionary findLatestBySourceId(Long sourceId) {

        return entityManager.createQuery(
                """
                SELECT d
                FROM Dictionary d
                WHERE d.source.id = :sourceId
                ORDER BY d.version DESC
                """,
                Dictionary.class
        )
        .setParameter("sourceId", sourceId)
        .setMaxResults(1)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Esta fonte não possui dicionário de dados cadastrado."
        ));
    }
}