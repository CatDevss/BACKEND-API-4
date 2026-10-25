package catdevs.georuraldatahub.service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import catdevs.georuraldatahub.dto.DictionaryRequestDTO;
import catdevs.georuraldatahub.dto.DictionaryResponseDTO;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.repository.DictionaryRepository;
import catdevs.georuraldatahub.repository.IngestionRepository;
import catdevs.georuraldatahub.repository.SourceRepository;
import catdevs.georuraldatahub.validation.FieldRule;

@Service
public class DictionaryService {

    private final DictionaryRepository dictionaries;
    private final SourceRepository sources;
    private final IngestionRepository ingestions;

    private final ObjectMapper mapper = new ObjectMapper();

    public DictionaryService(
            DictionaryRepository dictionaries,
            SourceRepository sources,
            IngestionRepository ingestions
    ) {
        this.dictionaries = dictionaries;
        this.sources = sources;
        this.ingestions = ingestions;
    }

    @Transactional(readOnly = true)
    public Dictionary findLatestBySourceId(Long sourceId) {
        return dictionaries.findFirstBySource_IdOrderByVersionDesc(sourceId)
                .orElseThrow(() -> missing(
                        "Esta fonte não possui dicionário de dados cadastrado."
                ));
    }

    // Seleciona o dicionário mais recente para a validação.
    @Transactional
    public Dictionary acquireForValidation(Long sourceId) {
        lockSource(sourceId);

        return findLatestBySourceId(sourceId);
    }

    @Transactional
    public DictionaryResponseDTO create(
            Long sourceId,
            DictionaryRequestDTO request
    ) {
        Source source = lockSource(sourceId);

        Dictionary dictionary = new Dictionary();
        dictionary.setSource(source);

        int nextVersion = dictionaries
                .findFirstBySource_IdOrderByVersionDesc(sourceId)
                .map(previous -> {
                    Integer version = previous.getVersion();

                    if (version == null || version < 1) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Dicionário existente possui versão inválida."
                        );
                    }

                    return version + 1;
                })
                .orElse(1);

        if (nextVersion > 99) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Esta fonte atingiu o limite de 99 versões permitido pelo banco atual."
            );
        }

        dictionary.setVersion(nextVersion);

        apply(dictionary, request);

        return response(dictionaries.save(dictionary));
    }

    @Transactional(readOnly = true)
    public List<DictionaryResponseDTO> list(Long sourceId) {
        if (!sources.existsById(sourceId)) {
            throw missing("Fonte não encontrada.");
        }

        return dictionaries
                .findAllBySource_IdOrderByVersionDesc(sourceId)
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
            DictionaryRequestDTO request
    ) {
        Dictionary dictionary = lockDictionary(id);

        ensureDraft(dictionary);
        apply(dictionary, request);

        return response(dictionaries.save(dictionary));
    }

    @Transactional
    public void delete(Long id) {
        Dictionary dictionary = lockDictionary(id);

        ensureDraft(dictionary);

        dictionaries.delete(dictionary);
    }

    @Transactional
    public DictionaryResponseDTO newVersion(Long id) {
        Dictionary dictionary = lockDictionary(id);

        return create(
                dictionary.getSource().getId(),
                new DictionaryRequestDTO(
                        dictionary.getName(),
                        dictionary.getDescription(),
                        rules(dictionary)
                )
        );
    }

    public List<FieldRule> rules(Dictionary dictionary) {
        try {
            List<FieldRule> rules = mapper.readValue(
                    dictionary.getStructure(),
                    new TypeReference<List<FieldRule>>() {}
            );

            validateRules(rules);

            return rules;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Estrutura de regras do dicionário inválida.",
                    e
            );
        }
    }

    private Dictionary lockDictionary(Long id) {
        Long sourceId = dictionaries.findSourceIdById(id)
                .orElseThrow(() -> missing("Dicionário não encontrado."));

        lockSource(sourceId);

        return dictionaries.findLockedById(id)
                .orElseThrow(() -> missing("Dicionário não encontrado."));
    }

    private Source lockSource(Long id) {
        return sources.findLockedById(id)
                .orElseThrow(() -> missing("Fonte não encontrada."));
    }

    private Dictionary entity(Long id) {
        return dictionaries.findById(id)
                .orElseThrow(() -> missing("Dicionário não encontrado."));
    }

    private boolean used(Dictionary dictionary) {
        return ingestions.existsByDictionary_Id(dictionary.getId());
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
            DictionaryRequestDTO request
    ) {
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
        } catch (Exception e) {
            throw new IllegalArgumentException("Regras inválidas.", e);
        }

        ensureFits(structure, 10000, "Estrutura de regras");

        dictionary.setName(name);
        dictionary.setDescription(request.description());
        dictionary.setStructure(structure);
    }

    private void ensureFits(
            String value,
            int maxBytes,
            String field
    ) {
        if (value != null
                && value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(
                    field + " excede o limite de " + maxBytes
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