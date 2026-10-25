package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.entity.Layer;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.exception.DataSetNotFoundException;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.util.GeoJsonFeatureExtractor.Extraction;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Persistência de compatibilidade enquanto as tabelas de execuções não estão habilitadas. */
@Service
public class LegacySpatialLayerStore {
    private final DatasetRepository datasets;
    private final FileRepository files;
    private final VersionRepository versions;
    private final LayerRepository layers;
    private final FeatureService features;
    private final BucketService bucket;
    private final jakarta.persistence.EntityManager entityManager;

    public LegacySpatialLayerStore(DatasetRepository datasets, FileRepository files, VersionRepository versions,
            LayerRepository layers, FeatureService features, jakarta.persistence.EntityManager entityManager, BucketService bucket) {
        this.datasets = datasets;
        this.files = files;
        this.versions = versions;
        this.layers = layers;
        this.features = features;
        this.bucket = bucket;
        this.entityManager = entityManager;
    }

    public record Input(Long datasetId, Long fileId, Long versionId, String location, String rawLocation) { }

    @Transactional(readOnly = true)
    public Input load(Long datasetId, Long fileId) {
        var dataset = datasets.findById(datasetId)
                .orElseThrow(() -> new DataSetNotFoundException("Conjunto não encontrado."));
        var file = dataset.getFiles().stream().filter(f -> f.getId().equals(fileId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado neste conjunto."));
        String format = file.getFormatFile() == null ? "" : file.getFormatFile().toLowerCase(Locale.ROOT);
        if (!Set.of("geojson", "json", "csv", "zip", "gpkg").contains(format))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Formato não suportado para carga espacial: " + format);
        var version = versions.findFirstByFile_IdAndStatusAndLocationStartingWithOrderByVersionNumberDesc(
                fileId, VersionStatus.BRUTA, "validada/")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Arquivo sem versão validada pendente de carga (ou já carregada)."));
        return new Input(datasetId, fileId, version.getId(), version.getLocation(), file.getLocation());
    }

    @Transactional(rollbackFor = Exception.class)
    public SpatialLayerService.LayerLoadResult complete(Input input, Extraction extraction) throws java.io.IOException {
        // Mesma ordem de bloqueio do tratamento novo. A segunda requisição revalida após esperar a primeira.
        var file = files.findLockedById(input.fileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
        entityManager.refresh(file);
        var dataset = datasets.findLockedById(input.datasetId())
                .orElseThrow(() -> new DataSetNotFoundException("Conjunto não encontrado."));
        var version = versions.findById(input.versionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Versão de entrada removida."));
        // Open EntityManager in View pode manter entidades da leitura anterior no cache da requisição.
        entityManager.refresh(dataset);
        entityManager.refresh(version);
        if (version.getStatus() != VersionStatus.BRUTA || !input.location().equals(version.getLocation())
                || !input.location().startsWith("validada/")
                || !file.getId().equals(version.getFile().getId())
                || dataset.getFiles().stream().noneMatch(f -> f.getId().equals(input.fileId())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Arquivo já carregado ou associação/versão alterada durante a leitura.");
        String target = BucketService.workingLocation(input.location(), "tratada/");
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            try { bucket.deleteObject(target); }
                            catch (RuntimeException error) { org.slf4j.LoggerFactory.getLogger(LegacySpatialLayerStore.class)
                                    .error("Falha ao limpar cópia de trabalho {}", target, error); }
                        }
                    }
                });
        bucket.copyWorkingObject(input.location(), target);
        var layer = new Layer();
        layer.setDataset(dataset);
        layer.setVersion(version);
        layer.setName(file.getName());
        layer.setGeometryType(extraction.geometryType());
        layer.setSrid(4326);
        layer = layers.saveAndFlush(layer);
        int inserted = features.insertBatch(layer.getId(), 4326, extraction.features());
        version.setLocation(target);
        version.setStatus(VersionStatus.TRATADA);
        versions.saveAndFlush(version);
        return new SpatialLayerService.LayerLoadResult(layer.getId(), layer.getName(), layer.getGeometryType(), 4326, inserted);
    }
}
