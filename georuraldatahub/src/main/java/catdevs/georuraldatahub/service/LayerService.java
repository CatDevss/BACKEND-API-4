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
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FileRepository;


@Service
public class LayerService {

    private static final Set<String> RENDERABLE = Set.of("geojson", "json", "csv");
    private static final long MAX_RENDER_BYTES = 50L * 1024 * 1024; // proteção do mapa e da memória

    private final FileRepository fileRepository;
    private final DatasetRepository datasetRepository;
    private final BucketService bucketService;

    public LayerService(FileRepository fileRepository, DatasetRepository datasetRepository,
            BucketService bucketService) {
        this.fileRepository = fileRepository;
        this.datasetRepository = datasetRepository;
        this.bucketService = bucketService;
    }

    @Transactional(readOnly = true)
    public List<LayerInfoDTO> listLayers(Long datasetId) {
        if (!datasetRepository.existsById(datasetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conjunto não encontrado");
        }
        return fileRepository.findByDatasetId(datasetId).stream()
                .filter(f -> isRenderable(f.getFormatFile()))
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
        if (file.getSize() != null && file.getSize() > MAX_RENDER_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                    "Arquivo grande demais para visualizar no mapa.");
        }

        return "csv".equalsIgnoreCase(file.getFormatFile())
                ? bucketService.readCsvAsGeoJson(file.getLocation())
                : bucketService.readGeoJson(file.getLocation());
    }

    private boolean isRenderable(String format) {
        return format != null && RENDERABLE.contains(format.toLowerCase(Locale.ROOT));
    }
}