package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.SourceCreateRequestDTO;
import catdevs.georuraldatahub.dto.SourceResponseDTO;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.entity.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;

@Service
public class SourceService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public SourceResponseDTO create(SourceCreateRequestDTO request) {

        String name = request.name().trim();

        if (existsByName(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Já existe uma fonte com esse nome"
            );
        }

        User user = entityManager.find(User.class, request.userId());

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Usuário não encontrado"
            );
        }

        Source source = new Source();
        source.setName(name);
        source.setUrl(request.url().trim());
        source.setUser(user);

        try {
            entityManager.persist(source);
            entityManager.flush();

            return new SourceResponseDTO(
                    source.getId(),
                    source.getName(),
                    source.getDateCreation(),
                    source.getUrl(),
                    source.getUser().getId()
            );

        } catch (DataIntegrityViolationException e) {

            if (isUniqueConstraintViolation(e)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Já existe uma fonte com esse nome",
                        e
                );
            }

            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<SourceResponseDTO> listAll() {

        return entityManager.createQuery(
                """
                SELECT s
                FROM Source s
                ORDER BY s.id DESC
                """,
                Source.class
        )
        .getResultList()
        .stream()
        .map(source -> new SourceResponseDTO(
                source.getId(),
                source.getName(),
                source.getDateCreation(),
                source.getUrl(),
                source.getUser() != null
                        ? source.getUser().getId()
                        : null
        ))
        .toList();
    }

    private boolean existsByName(String name) {

        Long quantity = entityManager.createQuery(
                """
                SELECT COUNT(s)
                FROM Source s
                WHERE LOWER(s.name) = LOWER(:name)
                """,
                Long.class
        )
        .setParameter("name", name)
        .getSingleResult();

        return quantity > 0;
    }

    private boolean isUniqueConstraintViolation(
            DataIntegrityViolationException exception) {

        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == 1) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}