package catdevs.georuraldatahub.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.entity.Dictionary;
import catdevs.georuraldatahub.repository.DictionaryRepository;

@Service
public class DictionaryService {
    private final DictionaryRepository dictionaryRepository;

    public DictionaryService(
            DictionaryRepository dictionaryRepository) {
        this.dictionaryRepository = dictionaryRepository;
    }

    @Transactional(readOnly = true)
    public Dictionary findLatestBySourceId(Long sourceId) {
        return dictionaryRepository
                .findFirstBySource_IdOrderByVersionDesc(sourceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Esta fonte não possui dicionário de dados cadastrado."));
    }
}