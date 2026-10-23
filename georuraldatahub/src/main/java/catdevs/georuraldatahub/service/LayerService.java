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

@Service
public class LayerService {

    private static final Set<String> RENDERABLE =
            Set.of("geojson", "json", "csv");

    private final FileService fileService;
    private final BucketService bucketService;

    public LayerService(FileService fileService, BucketService bucketService) {
        this.fileService = fileService;
        this.bucketService = bucketService;
    }

    @Transactional(readOnly = true)
    public List<LayerInfoDTO> listLayers(Long datasetId) {
        return fileService.findEntitiesByDatasetId(datasetId)
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
        File file = fileService.findEntityById(fileId);

        if (!isRenderable(file.getFormatFile())) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "Formato não suportado para visualização: "
                            + file.getFormatFile()
            );
        }

        if ("csv".equalsIgnoreCase(file.getFormatFile())) {
            return bucketService.readCsvAsGeoJson(file.getLocation());
        }

        return bucketService.readGeoJson(file.getLocation());
    }

    private boolean isRenderable(String format) {
        return format != null
                && RENDERABLE.contains(format.toLowerCase(Locale.ROOT));
    }
}