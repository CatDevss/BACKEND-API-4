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
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;
import catdevs.georuraldatahub.entity.Version;
import catdevs.georuraldatahub.entity.enums.VersionStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class LayerService {

    private static final Set<String> RENDERABLE_FORMATS =
            Set.of("geojson", "json", "csv");

    private static final String VALIDATED_LOCATION_PREFIX = "validada/";

    @PersistenceContext
    private EntityManager entityManager;

    private final BucketService bucketService;

    public LayerService(BucketService bucketService) {
        this.bucketService = bucketService;
    }

    @Transactional(readOnly = true)
    public List<LayerInfoDTO> listLayers(Long datasetId) {
        Dataset dataset = entityManager.find(Dataset.class, datasetId);

        if (dataset == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Dataset não encontrado."
            );
        }

        return entityManager.createQuery(
                """
                SELECT f
                FROM Dataset d
                JOIN d.files f
                WHERE d.id = :datasetId
                ORDER BY f.id DESC
                """,
                File.class
        )
        .setParameter("datasetId", datasetId)
        .getResultList()
        .stream()
        .filter(file -> isRenderable(file.getFormatFile()))
        .filter(this::hasValidatedVersion)
        .map(file -> new LayerInfoDTO(
                file.getId(),
                file.getName(),
                file.getFormatFile(),
                file.getSize(),
                file.getReceivedDate()
        ))
        .toList();
    }

    private boolean hasValidatedVersion(File file) {
        return findValidatedVersion(file.getId()) != null;
    }

    private Version findValidatedVersion(Long fileId) {
        List<Version> versions = entityManager.createQuery(
                """
                SELECT v
                FROM Version v
                WHERE v.file.id = :fileId
                  AND v.location LIKE :location
                ORDER BY v.versionNumber DESC
                """,
                Version.class
        )
        .setParameter("fileId", fileId)
        .setParameter("location", VALIDATED_LOCATION_PREFIX + "%")
        .setMaxResults(1)
        .getResultList();

        return versions.isEmpty() ? null : versions.get(0);
    }

    @Transactional(readOnly = true)
    public JsonNode getFileLayer(Long fileId) throws IOException {
        File file = entityManager.find(File.class, fileId);

        if (file == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Arquivo não encontrado."
            );
        }

        String format = file.getFormatFile();

        if (format == null || format.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Formato de arquivo não informado."
            );
        }

        if (!isRenderable(format)) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Formato de arquivo não suportado para visualização."
            );
        }

        Version version = findValidatedVersion(fileId);

        if (version == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "O arquivo não possui uma versão validada para visualização."
            );
        }

        String location = version.getLocation();

        if (location == null || location.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A versão validada não possui localização no bucket."
            );
        }

        return switch (format.toLowerCase(Locale.ROOT)) {
            case "geojson", "json" -> bucketService.readGeoJson(location);
            case "csv" -> bucketService.readCsvAsGeoJson(location);
            default -> throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Formato de arquivo não suportado para visualização."
            );
        };
    }

    private boolean isRenderable(String format) {
        return format != null
                && RENDERABLE_FORMATS.contains(
                        format.toLowerCase(Locale.ROOT)
                );
    }
}