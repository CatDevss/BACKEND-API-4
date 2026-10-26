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

    private static final Set<String> RENDERABLE = Set.of("geojson", "json", "csv", "zip", "gpkg");
    /** Apenas cópias aprovadas para visualização; o original bruto nunca é usado como camada. */
    private static final Set<VersionStatus> APPROVED_STATUSES =
            Set.of(VersionStatus.TRATADA, VersionStatus.PUBLICADA);

    private final FileRepository fileRepository;
    private final DatasetRepository datasetRepository;
    private final BucketService bucketService;
    private final VersionRepository versionRepository;
    private final catdevs.georuraldatahub.repository.LayerRepository layerRepository;

    public LayerService(FileRepository fileRepository, DatasetRepository datasetRepository,
            BucketService bucketService, VersionRepository versionRepository,
            catdevs.georuraldatahub.repository.LayerRepository layerRepository) {
        this.fileRepository = fileRepository;
        this.datasetRepository = datasetRepository;
        this.bucketService = bucketService;
        this.versionRepository = versionRepository;
        this.layerRepository = layerRepository;
    }

    @Transactional(readOnly = true)
    public List<LayerInfoDTO> listLayers(Long datasetId) {
        if (!datasetRepository.existsById(datasetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conjunto não encontrado");
        }
        return fileRepository.findByDatasetId(datasetId).stream()
                .filter(f -> isRenderable(f.getFormatFile()))
                .filter(f -> versionRepository.findFirstByFile_IdOrderByVersionNumberDesc(f.getId()).filter(this::approvedWorkingCopy).isPresent())
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

        String location = versionRepository.findFirstByFile_IdOrderByVersionNumberDesc(fileId)
                .filter(this::approvedWorkingCopy)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Arquivo sem registros aprovados para visualização.")).getLocation();
        return location.toLowerCase(Locale.ROOT).endsWith(".csv")
                ? bucketService.readCsvAsGeoJson(location)
                : bucketService.readGeoJson(location);
    }

    @Transactional(readOnly = true)
    public JsonNode getTreatedLayer(Long layerId) throws IOException {
        var layer = layerRepository.findById(layerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Camada não encontrada."));
        var version = layer.getVersion();
        if (version == null || !Set.of(VersionStatus.TRATADA, VersionStatus.PUBLICADA).contains(version.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Camada sem resultado tratado aprovado.");
        // A versão vinculada à camada é fixa; não buscar a versão mais recente do arquivo aqui.
        String location = version.getLocation();
        return location.toLowerCase(Locale.ROOT).endsWith(".csv")
                ? bucketService.readCsvAsGeoJson(location) : bucketService.readGeoJson(location);
    }

    private boolean approvedWorkingCopy(catdevs.georuraldatahub.entity.Version version) {
        return APPROVED_STATUSES.contains(version.getStatus()) && version.getLocation() != null
                && (version.getLocation().startsWith("tratada/") || version.getLocation().startsWith("publicada/"));
    }

    private boolean isRenderable(String format) {
        return format != null && RENDERABLE.contains(format.toLowerCase(Locale.ROOT));
    }
}