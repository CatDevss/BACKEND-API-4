package catdevs.georuraldatahub.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.dto.OccurrenceDTO;
import catdevs.georuraldatahub.dto.OccurrenceHistoryDTO;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.Occurrence;
import catdevs.georuraldatahub.entity.OccurrenceHistory;
import catdevs.georuraldatahub.entity.User;
import catdevs.georuraldatahub.entity.enums.OccurrenceStatus;
import catdevs.georuraldatahub.validation.RecordValidationResult;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

@Service
public class OccurrenceService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void registerValidation(
            Long fileId,
            List<RecordValidationResult> results) {

        File file = entityManager.find(File.class, fileId);

        if (file == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Arquivo não encontrado.");
        }

        for (RecordValidationResult result : results) {
            if (result.valid()) {
                continue;
            }

            for (String error : result.errors()) {
                Occurrence occurrence = new Occurrence();

                occurrence.setFile(file);
                occurrence.setRecordIndex(result.recordIndex());
                occurrence.setFieldName(extractField(error));
                occurrence.setPosition(null);
                occurrence.setCode(classify(error));
                occurrence.setReason(error);
                occurrence.setStatus(OccurrenceStatus.ABERTA);
                occurrence.setValidationAttempt(1);

                entityManager.persist(occurrence);
                entityManager.flush();

                OccurrenceHistory history = new OccurrenceHistory(
                        occurrence,
                        null,
                        OccurrenceStatus.ABERTA,
                        null,
                        "CRIACAO_AUTOMATICA",
                        "Ocorrência criada durante a validação."
                );

                entityManager.persist(history);
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<OccurrenceDTO> list(
            OccurrenceStatus status,
            Long fileId,
            String code,
            String field,
            Pageable pageable) {

        StringBuilder jpql = new StringBuilder(
                "SELECT o FROM Occurrence o WHERE 1 = 1");

        StringBuilder countJpql = new StringBuilder(
                "SELECT COUNT(o) FROM Occurrence o WHERE 1 = 1");

        if (status != null) {
            jpql.append(" AND o.status = :status");
            countJpql.append(" AND o.status = :status");
        }

        if (fileId != null) {
            jpql.append(" AND o.file.id = :fileId");
            countJpql.append(" AND o.file.id = :fileId");
        }

        if (code != null && !code.isBlank()) {
            jpql.append(" AND o.code = :code");
            countJpql.append(" AND o.code = :code");
        }

        if (field != null && !field.isBlank()) {
            jpql.append(" AND o.fieldName = :field");
            countJpql.append(" AND o.fieldName = :field");
        }

        jpql.append(" ORDER BY o.createdAt DESC");

        TypedQuery<Occurrence> query =
                entityManager.createQuery(jpql.toString(), Occurrence.class);

        TypedQuery<Long> countQuery =
                entityManager.createQuery(countJpql.toString(), Long.class);

        if (status != null) {
            query.setParameter("status", status);
            countQuery.setParameter("status", status);
        }

        if (fileId != null) {
            query.setParameter("fileId", fileId);
            countQuery.setParameter("fileId", fileId);
        }

        if (code != null && !code.isBlank()) {
            query.setParameter("code", code);
            countQuery.setParameter("code", code);
        }

        if (field != null && !field.isBlank()) {
            query.setParameter("field", field);
            countQuery.setParameter("field", field);
        }

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<OccurrenceDTO> content = query.getResultList()
                .stream()
                .map(OccurrenceDTO::from)
                .toList();

        long total = countQuery.getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public OccurrenceDTO detail(Long id) {
        return OccurrenceDTO.from(findOccurrence(id));
    }

    @Transactional
    public OccurrenceDTO assign(
            Long id,
            Long userId,
            Long actorUserId,
            String details) {

        Occurrence occurrence = findOccurrence(id);
        ensureMutable(occurrence);

        User responsible = entityManager.find(User.class, userId);

        if (responsible == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Responsável não encontrado.");
        }

        User actor = findActor(actorUserId);

        occurrence.setAssignedTo(responsible);
        occurrence.setUpdatedAt(LocalDateTime.now());

        entityManager.merge(occurrence);

        entityManager.persist(new OccurrenceHistory(
                occurrence,
                occurrence.getStatus(),
                occurrence.getStatus(),
                actor,
                "ATRIBUICAO_RESPONSAVEL",
                details
        ));

        return OccurrenceDTO.from(occurrence);
    }

    @Transactional
    public OccurrenceDTO changeStatus(
            Long id,
            OccurrenceStatus target,
            Long actorUserId,
            String details) {

        Occurrence occurrence = findOccurrence(id);
        ensureMutable(occurrence);

        OccurrenceStatus previous = occurrence.getStatus();

        boolean allowed =
                (previous == OccurrenceStatus.ABERTA
                        && target == OccurrenceStatus.EM_ANALISE)
                ||
                (previous == OccurrenceStatus.EM_ANALISE
                        && (target == OccurrenceStatus.RESOLVIDA
                        || target == OccurrenceStatus.ENCERRADA));

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Transição de status não permitida.");
        }

        if ((target == OccurrenceStatus.RESOLVIDA
                || target == OccurrenceStatus.ENCERRADA)
                && occurrence.getAssignedTo() == null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Atribua um responsável antes de finalizar.");
        }

        User actor = findActor(actorUserId);

        occurrence.setStatus(target);
        occurrence.setUpdatedAt(LocalDateTime.now());

        if (target == OccurrenceStatus.RESOLVIDA
                || target == OccurrenceStatus.ENCERRADA) {
            occurrence.setResolvedAt(LocalDateTime.now());
        }

        entityManager.merge(occurrence);

        entityManager.persist(new OccurrenceHistory(
                occurrence,
                previous,
                target,
                actor,
                "ALTERACAO_STATUS",
                details
        ));

        return OccurrenceDTO.from(occurrence);
    }

    @Transactional(readOnly = true)
    public List<OccurrenceHistoryDTO> history(Long id) {
        findOccurrence(id);

        return entityManager.createQuery(
                """
                SELECT h FROM OccurrenceHistory h
                JOIN FETCH h.occurrence
                LEFT JOIN FETCH h.actor
                WHERE h.occurrence.id = :id
                ORDER BY h.createdAt ASC
                """,
                OccurrenceHistory.class)
                .setParameter("id", id)
                .getResultList()
                .stream()
                .map(OccurrenceHistoryDTO::from)
                .toList();
    }

    private Occurrence findOccurrence(Long id) {
        Occurrence occurrence =
                entityManager.find(Occurrence.class, id);

        if (occurrence == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Ocorrência não encontrada.");
        }

        return occurrence;
    }

    private User findActor(Long actorUserId) {
        if (actorUserId == null) {
            return null;
        }

        User actor = entityManager.find(User.class, actorUserId);

        if (actor == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Usuário executor não encontrado.");
        }

        return actor;
    }

    private void ensureMutable(Occurrence occurrence) {
        if (occurrence.getStatus() == OccurrenceStatus.RESOLVIDA
                || occurrence.getStatus() == OccurrenceStatus.ENCERRADA) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ocorrências finalizadas não podem ser alteradas.");
        }
    }

    private String classify(String error) {
        String normalized = error.toLowerCase();

        if (normalized.contains("obrigatório")) {
            return "CAMPO_OBRIGATORIO";
        }

        if (normalized.contains("numérico")) {
            return "TIPO_INVALIDO";
        }

        if (normalized.contains("geometria")) {
            return "GEOMETRIA_INVALIDA";
        }

        if (normalized.contains("coordenadas")) {
            return "COORDENADAS_INVALIDAS";
        }

        if (normalized.contains("geojson")) {
            return "FORMATO_INVALIDO";
        }

        return "ERRO_VALIDACAO";
    }

    private String extractField(String error) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern
                        .compile("Campo '([^']+)'")
                        .matcher(error);

        if (matcher.find()) {
            return matcher.group(1);
        }

        matcher = java.util.regex.Pattern
                .compile("Campo obrigatório ausente: (.+)")
                .matcher(error);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }
}