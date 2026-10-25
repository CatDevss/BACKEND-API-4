package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.entity.Source;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import catdevs.georuraldatahub.dto.*;
import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.validation.FieldRule;

@Service
public class DictionaryService {
    private final DictionaryRepository dictionaries;
    private final SourceRepository sources;
    private final IngestionRepository ingestions;
    private final ObjectMapper mapper = new ObjectMapper();

    public DictionaryService(DictionaryRepository dictionaries, SourceRepository sources,
            IngestionRepository ingestions) {
        this.dictionaries = dictionaries;
        this.sources = sources;
        this.ingestions = ingestions;
    }

    @Transactional(readOnly = true)
    public Dictionary findLatestBySourceId(Long sourceId) {
        return dictionaries.findFirstBySource_IdOrderByVersionDesc(sourceId)
                .orElseThrow(() -> missing("Esta fonte não possui dicionário de dados cadastrado."));
    }

    @Transactional
    public Dictionary acquireForValidation(Long sourceId, Long dictionaryId) {
        // O CRUD usa o mesmo bloqueio, evitando alterar regras entre a seleção e o primeiro uso.
        lockSource(sourceId);
        return findForValidation(sourceId, dictionaryId);
    }

    @Transactional(readOnly = true)
    public Dictionary findForValidation(Long sourceId, Long dictionaryId) {
        if (dictionaryId == null) return findLatestBySourceId(sourceId);
        if (dictionaryId <= 0) throw new IllegalArgumentException("Informe um ID de dicionário positivo.");
        Dictionary dictionary = entity(dictionaryId);
        if (!Objects.equals(dictionary.getSource().getId(), sourceId))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "O dicionário selecionado não pertence à fonte informada.");
        return dictionary;
    }

    @Transactional
    public DictionaryResponseDTO create(Long sourceId, DictionaryRequestDTO request) {
        var source = lockSource(sourceId);
        Dictionary d = new Dictionary();
        d.setSource(source);
        int nextVersion = dictionaries.findFirstBySource_IdOrderByVersionDesc(sourceId)
                .map(previous -> {
                    Integer version = previous.getVersion();
                    if (version == null || version < 1) throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Dicionário existente possui versão inválida.");
                    return version + 1;
                }).orElse(1);
        if (nextVersion > 99) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Esta fonte atingiu o limite de 99 versões permitido pelo banco atual.");
        d.setVersion(nextVersion);
        apply(d, request);
        return response(dictionaries.save(d));
    }

    @Transactional(readOnly = true)
    public List<DictionaryResponseDTO> list(Long sourceId) {
        if (!sources.existsById(sourceId)) throw missing("Fonte não encontrada.");
        return dictionaries.findBySource_IdOrderByVersionDesc(sourceId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public DictionaryResponseDTO get(Long id) { return response(entity(id)); }

    @Transactional
    public DictionaryResponseDTO update(Long id, DictionaryRequestDTO request) {
        Dictionary d = lockDictionary(id);
        ensureDraft(d);
        apply(d, request);
        return response(dictionaries.save(d));
    }

    @Transactional
    public void delete(Long id) {
        Dictionary d = lockDictionary(id);
        ensureDraft(d);
        dictionaries.delete(d);
    }

    @Transactional
    public DictionaryResponseDTO newVersion(Long id) {
        Dictionary d = lockDictionary(id);
        return create(d.getSource().getId(), new DictionaryRequestDTO(d.getName(), d.getDescription(), rules(d)));
    }

    public List<FieldRule> rules(Dictionary d) {
        try {
            List<FieldRule> rules = mapper.readValue(d.getStructure(), new TypeReference<List<FieldRule>>() {});
            validateRules(rules);
            return rules;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Estrutura de regras do dicionário inválida.", e);
        }
    }

    private Dictionary lockDictionary(Long id) {
        // Mantém a ordem fonte -> dicionário em todas as operações para reduzir risco de deadlock.
        lockSource(dictionaries.findSourceIdById(id).orElseThrow(() -> missing("Dicionário não encontrado.")));
        return dictionaries.findLockedById(id).orElseThrow(() -> missing("Dicionário não encontrado."));
    }
    private Source lockSource(Long id) {
        return sources.findLockedById(id).orElseThrow(() -> missing("Fonte não encontrada."));
    }
    private Dictionary entity(Long id) {
        return dictionaries.findById(id).orElseThrow(() -> missing("Dicionário não encontrado."));
    }
    private boolean used(Dictionary d) { return ingestions.existsByDictionary_Id(d.getId()); }
    private void ensureDraft(Dictionary d) {
        if (used(d)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Dicionário já utilizado em validações. Crie uma nova versão para alterar as regras.");
    }
    private void apply(Dictionary d, DictionaryRequestDTO request) {
        validateRules(request.rules());
        if (request.name() == null || request.name().isBlank()) throw new IllegalArgumentException("Nome obrigatório.");
        String name = request.name().trim();
        ensureFits(name, 100, "Nome");
        ensureFits(request.description(), 500, "Descrição");
        String structure;
        try { structure = mapper.writeValueAsString(request.rules()); }
        catch (Exception e) { throw new IllegalArgumentException("Regras inválidas.", e); }
        ensureFits(structure, 10000, "Estrutura de regras");
        d.setName(name);
        d.setDescription(request.description());
        d.setStructure(structure);
    }
    private void ensureFits(String value, int maxBytes, String field) {
        if (value != null && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes)
            throw new IllegalArgumentException(field + " excede o limite de " + maxBytes + " bytes do banco atual.");
    }
    private void validateRules(List<FieldRule> rules) {
        if (rules == null || rules.isEmpty()) throw new IllegalArgumentException("Informe ao menos uma regra.");
        Set<String> names = new HashSet<>();
        for (FieldRule rule : rules) {
            if (rule == null || rule.name() == null || rule.name().isBlank() || rule.type() == null
                    || !rule.name().equals(rule.name().trim()) || !names.add(rule.name()))
                throw new IllegalArgumentException("Campos devem ter nome único, sem espaços nas extremidades, e tipo válido.");
        }
    }
    private DictionaryResponseDTO response(Dictionary d) {
        return new DictionaryResponseDTO(d.getId(), d.getSource().getId(), d.getName(), d.getDescription(),
                d.getVersion(), used(d) ? "UTILIZADO" : "RASCUNHO", rules(d), d.getCreatedOn());
    }
    private ResponseStatusException missing(String reason) { return new ResponseStatusException(HttpStatus.NOT_FOUND, reason); }
}
