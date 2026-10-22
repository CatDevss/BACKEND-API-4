
package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.dto.DictionaryRequestDTO;
import catdevs.georuraldatahub.dto.DictionaryResponseDTO;
import catdevs.georuraldatahub.entity.DataIngestion;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.validation.FieldRule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DictionaryService {

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper mapper = new ObjectMapper();

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
        .orElseThrow(() -> missing(
                "Esta fonte não possui dicionário de dados cadastrado."
        ));
    }

    @Transactional
    public Dictionary acquireForValidation(Long sourceId) {
        lockSource(sourceId);
        return findLatestBySourceId(sourceId);
    }

    @Transactional
    public DictionaryResponseDTO create(
            Long sourceId,
            DictionaryRequestDTO request) {

        Source source = lockSource(sourceId);

        List<Dictionary> previousVersions = entityManager.createQuery(
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
        .getResultList();

        int nextVersion = 1;

        if (!previousVersions.isEmpty()) {
            Integer version = previousVersions.get(0).getVersion();

            if (version == null || version < 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Dicionário existente possui versão inválida."
                );
            }

            nextVersion = version + 1;
        }

        if (nextVersion > 99) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Esta fonte atingiu o limite de 99 versões permitido pelo banco atual."
            );
        }

        Dictionary dictionary = new Dictionary();
        dictionary.setSource(source);
        dictionary.setVersion(nextVersion);

        apply(dictionary, request);

        entityManager.persist(dictionary);
        entityManager.flush();

        return response(dictionary);
    }

    @Transactional(readOnly = true)
    public List<DictionaryResponseDTO> list(Long sourceId) {
        Source source = entityManager.find(Source.class, sourceId);

        if (source == null) {
            throw missing("Fonte não encontrada.");
        }

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
        .getResultList()
        .stream()
        .map(this::response)
        .toList();
    }

    @Transactional(readOnly = true)
    public DictionaryResponseDTO get(Long id) {
        return response(entity(id));
    }

    @Transactional
    public DictionaryResponseDTO update(
            Long id,
            DictionaryRequestDTO request) {

        Dictionary dictionary = lockDictionary(id);

        ensureDraft(dictionary);
        apply(dictionary, request);

        return response(dictionary);
    }

    @Transactional
    public void delete(Long id) {
        Dictionary dictionary = lockDictionary(id);

        ensureDraft(dictionary);

        entityManager.remove(dictionary);
    }

    @Transactional
    public DictionaryResponseDTO newVersion(Long id) {
        Dictionary dictionary = lockDictionary(id);

        DictionaryRequestDTO request = new DictionaryRequestDTO(
                dictionary.getName(),
                dictionary.getDescription(),
                rules(dictionary)
        );

        return create(dictionary.getSource().getId(), request);
    }

    public List<FieldRule> rules(Dictionary dictionary) {
        try {
            List<FieldRule> rules = mapper.readValue(
                    dictionary.getStructure(),
                    new TypeReference<List<FieldRule>>() {}
            );

            validateRules(rules);

            return rules;

        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Estrutura de regras do dicionário inválida.",
                    exception
            );
        }
    }

    private Dictionary lockDictionary(Long id) {
        Dictionary dictionary = entity(id);

        lockSource(dictionary.getSource().getId());

        Dictionary locked = entityManager.find(
                Dictionary.class,
                id,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (locked == null) {
            throw missing("Dicionário não encontrado.");
        }

        return locked;
    }

    private Source lockSource(Long id) {
        Source source = entityManager.find(
                Source.class,
                id,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (source == null) {
            throw missing("Fonte não encontrada.");
        }

        return source;
    }

    private Dictionary entity(Long id) {
        Dictionary dictionary = entityManager.find(
                Dictionary.class,
                id
        );

        if (dictionary == null) {
            throw missing("Dicionário não encontrado.");
        }

        return dictionary;
    }

    private boolean used(Dictionary dictionary) {
        Long count = entityManager.createQuery(
                """
                SELECT COUNT(i)
                FROM DataIngestion i
                WHERE i.dictionary.id = :dictionaryId
                """,
                Long.class
        )
        .setParameter("dictionaryId", dictionary.getId())
        .getSingleResult();

        return count > 0;
    }

    private void ensureDraft(Dictionary dictionary) {
        if (used(dictionary)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Dicionário já utilizado em validações. Crie uma nova versão para alterar as regras."
            );
        }
    }

    private void apply(
            Dictionary dictionary,
            DictionaryRequestDTO request) {

        validateRules(request.rules());

        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Nome obrigatório.");
        }

        String name = request.name().trim();

        ensureFits(name, 100, "Nome");
        ensureFits(request.description(), 500, "Descrição");

        String structure;

        try {
            structure = mapper.writeValueAsString(request.rules());
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Regras inválidas.",
                    exception
            );
        }

        ensureFits(structure, 10000, "Estrutura de regras");

        dictionary.setName(name);
        dictionary.setDescription(request.description());
        dictionary.setStructure(structure);
    }

    private void ensureFits(
            String value,
            int maxBytes,
            String field) {

        if (value != null
                && value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {

            throw new IllegalArgumentException(
                    field + " excede o limite de "
                            + maxBytes
                            + " bytes do banco atual."
            );
        }
    }

    private void validateRules(List<FieldRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException(
                    "Informe ao menos uma regra."
            );
        }

        Set<String> names = new HashSet<>();

        for (FieldRule rule : rules) {
            if (rule == null
                    || rule.name() == null
                    || rule.name().isBlank()
                    || rule.type() == null
                    || !rule.name().equals(rule.name().trim())
                    || !names.add(rule.name())) {

                throw new IllegalArgumentException(
                        "Campos devem ter nome único, sem espaços nas extremidades, e tipo válido."
                );
            }
        }
    }

    private DictionaryResponseDTO response(Dictionary dictionary) {
        return new DictionaryResponseDTO(
                dictionary.getId(),
                dictionary.getSource().getId(),
                dictionary.getName(),
                dictionary.getDescription(),
                dictionary.getVersion(),
                used(dictionary) ? "UTILIZADO" : "RASCUNHO",
                rules(dictionary),
                dictionary.getCreatedOn()
        );
    }

    private ResponseStatusException missing(String reason) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                reason
        );
    }
}
