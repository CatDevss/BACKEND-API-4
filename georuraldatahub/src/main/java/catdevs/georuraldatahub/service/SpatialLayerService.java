package catdevs.georuraldatahub.service;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.Layer;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.enums.GeometryType;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FeatureRepository;
import catdevs.georuraldatahub.repository.LayerRepository;
import catdevs.georuraldatahub.repository.VersionRepository;
import catdevs.georuraldatahub.util.GeoJsonFeatureExtractor;
import catdevs.georuraldatahub.util.GeoJsonFeatureExtractor.Extraction;

/**
 * Carrega para o Oracle Spatial os registros validados (zona validada/ do
 * bucket) de um arquivo de um conjunto: cria a camada, grava as feições e
 * marca a versão como TRATADA.
 */
@Service
public class SpatialLayerService {

    private static final String VALIDATED_PREFIX = "validada/";
    /** SRID da coluna feicao.fei_geometria (metadado espacial) e do GeoJSON. */
    private static final int GEOJSON_SRID = 4326;
    private static final Set<String> SUPPORTED_FORMATS = Set.of("geojson", "json", "csv");

    private final DatasetRepository datasetRepository;
    private final VersionRepository versionRepository;
    private final LayerRepository layerRepository;
    private final FeatureRepository featureRepository;
    private final BucketService bucketService;

    public SpatialLayerService(DatasetRepository datasetRepository, VersionRepository versionRepository,
            LayerRepository layerRepository, FeatureRepository featureRepository,
            BucketService bucketService) {
        this.datasetRepository = datasetRepository;
        this.versionRepository = versionRepository;
        this.layerRepository = layerRepository;
        this.featureRepository = featureRepository;
        this.bucketService = bucketService;
    }

    public record LayerLoadResult(Long layerId, String name, GeometryType geometryType,
            int srid, int featureCount) {
    }

    @Transactional
    public LayerLoadResult createLayerFromFile(Long datasetId, Long fileId) throws IOException {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new DataSetNotFoundException("Conjunto não encontrado"));

        File file = dataset.getFiles().stream()
                .filter(f -> f.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Arquivo não encontrado neste conjunto."));

        String format = file.getFormatFile() == null ? "" : file.getFormatFile().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FORMATS.contains(format)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Formato não suportado para carga espacial: " + file.getFormatFile());
        }

        Version version = versionRepository
                .findFirstByFile_IdAndStatusAndLocationStartingWithOrderByVersionNumberDesc(
                        fileId, VersionStatus.BRUTA, VALIDATED_PREFIX)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Arquivo sem versão validada pendente de carga (ou já carregada)."));

        JsonNode geoJson = "csv".equals(format)
                ? bucketService.readCsvAsGeoJson(version.getLocation())
                : bucketService.readGeoJson(version.getLocation());
        Extraction extraction = GeoJsonFeatureExtractor.extract(geoJson);

        Layer layer = new Layer();
        layer.setDataset(dataset);
        layer.setVersion(version);
        layer.setName(file.getName());
        layer.setGeometryType(extraction.geometryType());
        layer.setSrid(GEOJSON_SRID);
        layerRepository.saveAndFlush(layer);

        int inserted = featureRepository.insertBatch(layer.getId(), GEOJSON_SRID, extraction.features());

        // Só depois das feições: o trg_proteger_feicao bloqueia inserções em versão TRATADA.
        version.setStatus(VersionStatus.TRATADA);
        versionRepository.save(version);

        return new LayerLoadResult(layer.getId(), layer.getName(), layer.getGeometryType(),
                GEOJSON_SRID, inserted);
    }
}