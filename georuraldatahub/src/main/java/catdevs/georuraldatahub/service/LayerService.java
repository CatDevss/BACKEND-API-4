package catdevs.georuraldatahub.service;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

import catdevs.georuraldatahub.dto.LayerInfoDTO;
import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.entity.File;

@Service
public class LayerService {

    private static final Set<String> RENDERABLE =
            Set.of("geojson", "json", "csv");

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
                    "Conjunto não encontrado"
            );
        }

        return entityManager.createQuery(
                """
                SELECT f
                FROM Dataset d JOIN d.files f
                WHERE d.id = :datasetId
                ORDER BY f.id DESC
                """,
                File.class
        )
        .setParameter("datasetId", datasetId)
        .getResultList()
        .stream()
        .filter(file -> isRenderable(file.getFormatFile()))
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

        File file = entityManager.find(File.class, fileId);

        if (file == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Arquivo não encontrado."
            );
        }

        if (!isRenderable(file.getFormatFile())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Formato não suportado para visualização: "
                            + file.getFormatFile()
            );
        }

        return "csv".equalsIgnoreCase(file.getFormatFile())
                ? bucketService.readCsvAsGeoJson(file.getLocation())
                : bucketService.readGeoJson(file.getLocation());
    }

    private boolean isRenderable(String format) {
        return format != null
                && RENDERABLE.contains(format.toLowerCase(Locale.ROOT));
    }
}