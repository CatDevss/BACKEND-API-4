package catdevs.georuraldatahub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import catdevs.georuraldatahub.service.FileService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin
public class GeospatialMetadataController {
    private final FileService files;
    public GeospatialMetadataController(FileService files) { this.files = files; }
    @GetMapping("/arquivos/{fileId}/metadados-geoespaciais")
    public ResponseEntity<JsonNode> metadata(@PathVariable Long fileId) throws Exception {
        return ResponseEntity.status(HttpStatus.OK).body(files.geospatialMetadata(fileId));
    }
}
