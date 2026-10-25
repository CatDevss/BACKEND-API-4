package catdevs.georuraldatahub.processing;

import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.service.*;
import catdevs.georuraldatahub.validation.FieldRule;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TreatmentInputService {
    private final FileRepository files;
    private final VersionRepository versions;
    private final UserService users;
    private final DictionaryService dictionaries;
    public TreatmentInputService(FileRepository files, VersionRepository versions, UserService users, DictionaryService dictionaries) {
        this.files = files; this.versions = versions; this.users = users; this.dictionaries = dictionaries;
    }
    public record Input(Long fileId, Long inputVersionId, String name, String format, String rawLocation,
            String validatedLocation, Long sourceId, String originalHash, Long dictionaryId,
            Integer dictionaryVersion, List<FieldRule> rules) { }
    @Transactional(readOnly = true)
    public Long uploaderId(Long fileId) {
        return files.findById(fileId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."))
                .getIngestion().getUser().getId();
    }
    @Transactional(readOnly = true)
    public Input load(Long fileId, Long userId) {
        if (fileId == null || fileId <= 0 || userId == null || userId <= 0) throw new IllegalArgumentException("Informe IDs positivos.");
        if (users.findEntityById(userId).getUserStatus() != 1)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário inativo não pode iniciar tratamento.");
        var file = files.findById(fileId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
        var version = versions.findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                fileId, Set.of(VersionStatus.BRUTA, VersionStatus.TRATADA, VersionStatus.PUBLICADA), "validada/")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Arquivo sem registros ou metadados aprovados para tratamento."));
        var dictionary = file.getIngestion().getDictionary();
        return new Input(fileId, version.getId(), file.getName(), file.getFormatFile().toLowerCase(Locale.ROOT),
                file.getLocation(), version.getLocation(), dictionary.getSource().getId(), file.getHash(),
                dictionary.getId(), dictionary.getVersion(), dictionaries.rules(dictionary));
    }
}
