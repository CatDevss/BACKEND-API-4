package catdevs.georuraldatahub.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import catdevs.georuraldatahub.dto.FileStageDTO;
import catdevs.georuraldatahub.repository.VersionRepository;

/** A etapa atual é definida pela versão mais recente do arquivo. */
@Service
public class FileStageService {

    private final VersionRepository versionRepository;

    public FileStageService(VersionRepository versionRepository) {
        this.versionRepository = versionRepository;
    }

    @Transactional(readOnly = true)
    public List<FileStageDTO> currentStages() {
        return versionRepository.findCurrentStageOfEachFile();
    }
}