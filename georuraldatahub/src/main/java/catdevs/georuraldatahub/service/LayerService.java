package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.repository.VersionRepository;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

import catdevs.georuraldatahub.dto.LayerInfoDTO;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FileRepository;

@Service
public class LayerService {

    private static final Set<String> RENDERABLE = Set.of("geojson", "json", "csv");
    /** Versões com registros validados: ainda pendentes de carga (BRUTA) ou já carregadas. */
    private static final Set<VersionStatus> VALIDATED_STATUSES =
            Set.of(VersionStatus.BRUTA, VersionStatus.TRATADA, VersionStatus.PUBLICADA);

    private final FileRepository fileRepository;
    private final DatasetRepository datasetRepository;
    private final BucketService bucketService;
    private final VersionRepository versionRepository;

    public LayerService(FileRepository fileRepository, DatasetRepository datasetRepository,
            BucketService bucketService, VersionRepository versionRepository) {
        this.fileRepository = fileRepository;
        this.datasetRepository = datasetRepository;
        this.bucketService = bucketService;
        this.versionRepository = versionRepository;
    }

    @Transactional(readOnly = true)
    public List<LayerInfoDTO> listLayers(Long datasetId) {
        if (!datasetRepository.existsById(datasetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conjunto não encontrado");
        }
        return fileRepository.findByDatasetId(datasetId).stream()
                .filter(f -> isRenderable(f.getFormatFile()))
                .filter(f -> versionRepository.findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                        f.getId(), VALIDATED_STATUSES, "validada/").isPresent())
                .map(f -> new LayerInfoDTO(f.getId(), f.getName(), f.getFormatFile(),
                        f.getSize(), f.getReceivedDate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public JsonNode getFileLayer(Long fileId) throws IOException {
        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."));

        if (!isRenderable(file.getFormatFile())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Formato não suportado para visualização: " + file.getFormatFile());
        }

        String location = versionRepository.findFirstByFile_IdAndStatusInAndLocationStartingWithOrderByVersionNumberDesc(
                fileId, VALIDATED_STATUSES, "validada/")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Arquivo sem registros aprovados para visualização.")).getLocation();
        return "csv".equalsIgnoreCase(file.getFormatFile())
                ? bucketService.readCsvAsGeoJson(location)
                : bucketService.readGeoJson(location);
    }

    private boolean isRenderable(String format) {
        return format != null && RENDERABLE.contains(format.toLowerCase(Locale.ROOT));
    }
}