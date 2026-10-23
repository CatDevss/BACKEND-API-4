package catdevs.georuraldatahub.service;

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
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FileRepository;
import catdevs.georuraldatahub.repository.VersionRepository;

@Service
public class LayerService {

    private static final Set<String> RENDERABLE =
            Set.of("geojson", "json", "csv");

    private static final String VALIDATED_PREFIX = "validada/";

    private final FileRepository files;
    private final DatasetRepository datasets;
    private final BucketService bucketService;
    private final VersionRepository versions;

    public LayerService(
            FileRepository files,
            DatasetRepository datasets,
            BucketService bucketService,
            VersionRepository versions) {
        this.files = files;
        this.datasets = datasets;
        this.bucketService = bucketService;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public List<LayerInfoDTO> listLayers(Long datasetId) {
        if (!datasets.existsById(datasetId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Conjunto de dados não encontrado."
            );
        }

        return files.findByDatasetId(datasetId)
                .stream()
                .filter(file -> isRenderable(file.getFormatFile()))
                .filter(file -> findValidatedVersion(file.getId()).isPresent())
                .map(file -> new LayerInfoDTO(
                        file.getId(),
                        file.getName(),
                        file.getFormatFile(),
                        file.getSize(),
                        file.getReceivedDate()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public JsonNode getFileLayer(Long fileId) throws IOException {
        File file = files.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Arquivo não encontrado."
                ));

        if (!isRenderable(file.getFormatFile())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Formato não suportado para visualização: "
                            + file.getFormatFile()
            );
        }

        Version version = findValidatedVersion(fileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "O arquivo não possui uma versão aprovada para visualização."
                ));

        String location = version.getLocation();

        if ("csv".equalsIgnoreCase(file.getFormatFile())) {
            return bucketService.readCsvAsGeoJson(location);
        }

        return bucketService.readGeoJson(location);
    }

    private java.util.Optional<Version> findValidatedVersion(Long fileId) {
        return versions
                .findFirstByFile_IdAndStatusAndLocationStartingWithOrderByVersionNumberDesc(
                        fileId,
                        VersionStatus.BRUTA,
                        VALIDATED_PREFIX
                );
    }

    private boolean isRenderable(String format) {
        return format != null
                && RENDERABLE.contains(format.toLowerCase(Locale.ROOT));
    }
}