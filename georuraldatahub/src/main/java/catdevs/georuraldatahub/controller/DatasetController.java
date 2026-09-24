package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.dto.DatasetCreateRequestDTO;
import catdevs.georuraldatahub.dto.DatasetResponseDTO;
import catdevs.georuraldatahub.service.DatasetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/conjuntos")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PostMapping
    public ResponseEntity<DatasetResponseDTO> create(
            @RequestBody DatasetCreateRequestDTO request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(datasetService.create(request));
    }

    @PostMapping(
            value = "/{id}/arquivos",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Void> uploadFile(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok().build();
    }
}