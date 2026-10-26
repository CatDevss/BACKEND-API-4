package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.transaction.annotation.Transactional;

import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.Layer;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.enums.GeometryType;
import catdevs.georuraldatahub.entity.enums.VersionStatus;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.LayerRepository;
import catdevs.georuraldatahub.repository.VersionRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class LayerMappingTest {

    @Autowired
    private LayerRepository layerRepository;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private VersionRepository versionRepository;
    @Autowired
    private EntityManager entityManager;

    private Layer newLayer(Dataset dataset, Version version) {
        Layer layer = new Layer();
        layer.setDataset(dataset);
        layer.setVersion(version);
        layer.setName("teste mapeamento");
        layer.setGeometryType(GeometryType.POINT);
        layer.setSrid(4326);
        return layer;
    }

    private Dataset anyDataset() {
        return datasetRepository.findAll().stream().findFirst().orElse(null);
    }

    private Version anyRawVersion() {
        return versionRepository.findAll().stream()
                .filter(v -> v.getStatus() == VersionStatus.BRUTA)
                .findFirst().orElse(null);
    }

    @Test
    void savesAndRenamesLayerOnUnapprovedVersion() {
        Dataset dataset = anyDataset();
        Version version = anyRawVersion();
        assumeTrue(dataset != null && version != null,
                "Precisa de um Dataset e uma Version BRUTA no banco");

        Layer layer = newLayer(dataset, version);
        layerRepository.saveAndFlush(layer);
        assertNotNull(layer.getId());

        layer.setName("teste renomeada");
        layerRepository.saveAndFlush(layer);
        entityManager.clear();

        Layer reloaded = layerRepository.findById(layer.getId()).orElseThrow();
        assertEquals("teste renomeada", reloaded.getName());
        assertEquals(4326, reloaded.getSrid());
        assertEquals(GeometryType.POINT, reloaded.getGeometryType());
        assertNotNull(reloaded.getCreatedAt());
    }

    @Test
    void blocksChangesWhenVersionIsApproved() {
        Dataset dataset = anyDataset();
        Version version = anyRawVersion();
        assumeTrue(dataset != null && version != null,
                "Precisa de um Dataset e uma Version BRUTA no banco");

        Layer layer = newLayer(dataset, version);
        layerRepository.saveAndFlush(layer);

        // Aprova a versão (desfeito pelo rollback do teste)
        version.setStatus(VersionStatus.TRATADA);
        versionRepository.saveAndFlush(version);

        layer.setName("nome novo");
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> layerRepository.saveAndFlush(layer));

        String cause = NestedExceptionUtils.getMostSpecificCause(error).getMessage();
        assertTrue(cause.contains("ORA-20002"), "Esperava ORA-20002 mas veio: " + cause);
    }
}