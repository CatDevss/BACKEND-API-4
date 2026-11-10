package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.repository.VersionRepository;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import java.io.IOException;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

import catdevs.georuraldatahub.dto.LayerInfoDTO;
import catdevs.georuraldatahub.dto.FileStageDTO;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FileRepository;

@Service
public class LayerService {

    private static final Set<String> RENDERABLE = Set.of("geojson", "json", "csv", "zip", "gpkg");
    private static final Set<VersionStatus> APPROVED_STATUSES = Set.of(VersionStatus.TRATADA, VersionStatus.PUBLICADA);
    private static final ObjectMapper REPORT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

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
        return listLayers(datasetId, fileRepository.findByDatasetId(datasetId));
    }

    // A estrutura do workspace já carregou e validou o conjunto e seus arquivos.
    List<LayerInfoDTO> listLayers(Long datasetId, List<File> associated) {
        var renderable = associated.stream().filter(f -> isRenderable(f.getFormatFile())).toList();
        if (renderable.isEmpty()) return List.of();
        var approved = new HashSet<>(versionRepository.findFileIdsByDatasetAndStatusIn(datasetId, APPROVED_STATUSES));
        var pending = renderable.stream().filter(f -> !approved.contains(f.getId()))
                .collect(Collectors.toMap(File::getId, Function.identity()));
        if (!pending.isEmpty()) {
            for (var stage : versionRepository.findLegacyValidatedStagesByDataset(datasetId, VersionStatus.BRUTA)) {
                var file = pending.get(stage.fileId());
                if (file != null && isApprovedLegacyPreview(file, stage)) approved.add(file.getId());
            }
        }
        return renderable.stream()
                .filter(f -> approved.contains(f.getId()))
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

        String location = versionRepository.findFirstByFile_IdAndStatusInOrderByVersionNumberDesc(
                fileId, APPROVED_STATUSES)
                .map(v -> v.getLocation())
                .orElseGet(() -> versionRepository.findFirstByFile_IdOrderByVersionNumberDesc(fileId)
                        .filter(v -> isApprovedLegacyPreview(file,
                                new FileStageDTO(fileId, v.getStatus(), v.getVersionNumber(), v.getLocation())))
                        .map(v -> v.getLocation())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                                "Arquivo sem registros aprovados para visualização.")));
        return location.toLowerCase(Locale.ROOT).endsWith(".csv")
                ? bucketService.readCsvAsGeoJson(location)
                : bucketService.readGeoJson(location);
    }

    @Transactional(readOnly = true)
    public JsonNode getTreatedLayer(Long layerId) throws IOException {
        var layer = layerRepository.findById(layerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Camada não encontrada."));
        var version = layer.getVersion();
        if (version == null || !APPROVED_STATUSES.contains(version.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Camada sem resultado tratado aprovado.");
        // A versão vinculada à camada é fixa; não buscar a versão mais recente do arquivo aqui.
        String location = version.getLocation();
        return location.toLowerCase(Locale.ROOT).endsWith(".csv")
                ? bucketService.readCsvAsGeoJson(location) : bucketService.readGeoJson(location);
    }

    private boolean isRenderable(String format) {
        return format != null && RENDERABLE.contains(format.toLowerCase(Locale.ROOT));
    }

    private boolean isApprovedLegacyPreview(File file, FileStageDTO stage) {
        if (stage.status() != VersionStatus.BRUTA || stage.versionNumber() == null
                || stage.versionNumber() <= 1 || stage.location() == null) return false;
        String raw = file.getLocation();
        if (raw == null || !raw.startsWith("bruta/") || raw.length() <= 6) return false;
        String expected = "validada/" + raw.substring("bruta/".length());
        if (Set.of("zip", "gpkg").contains(file.getFormatFile().toLowerCase(Locale.ROOT))) expected += ".geojson";
        if (!stage.location().equals(expected)) return false;
        var ingestion = file.getIngestion();
        if (ingestion == null || ingestion.getDictionary() == null) return false;
        var dictionary = ingestion.getDictionary();
        try {
            // O caminho sozinho não comprova aprovação. Nunca expor o original bruto
            // nem os resultados parciais que a ingestão antiga também gravava em validada/.
            var report = REPORT_MAPPER.readTree(bucketService.readValidationReport(raw));
            return report != null && "VALIDO".equals(report.path("status").asText())
                    && report.path("validRecords").isIntegralNumber()
                    && report.path("validRecords").canConvertToLong()
                    && report.path("validRecords").longValue() > 0
                    && report.path("rejectedRecords").isIntegralNumber()
                    && report.path("rejectedRecords").canConvertToLong()
                    && report.path("rejectedRecords").longValue() == 0
                    && report.path("dictionaryId").isIntegralNumber()
                    && report.path("dictionaryId").canConvertToLong()
                    && Long.valueOf(report.path("dictionaryId").longValue()).equals(dictionary.getId())
                    && report.path("dictionaryVersion").isIntegralNumber()
                    && report.path("dictionaryVersion").canConvertToInt()
                    && Integer.valueOf(report.path("dictionaryVersion").intValue()).equals(dictionary.getVersion());
        } catch (IOException | ResponseStatusException | com.oracle.bmc.model.BmcException e) {
            org.slf4j.LoggerFactory.getLogger(LayerService.class)
                    .warn("Não foi possível confirmar a validação legada do arquivo {}", file.getId(), e);
            return false;
        }
    }
}
