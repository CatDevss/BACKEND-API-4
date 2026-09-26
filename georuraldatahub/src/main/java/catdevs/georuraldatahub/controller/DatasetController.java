package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.dto.DatasetCreateRequestDTO;
import catdevs.georuraldatahub.dto.DatasetResponseDTO;
import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.service.DatasetService;
import catdevs.georuraldatahub.service.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/conjuntos")
public class DatasetController {

    private final DatasetService datasetService;
    private final FileService fileService;

    public DatasetController(
            DatasetService datasetService,
            FileService fileService
    ) {
        this.datasetService = datasetService;
        this.fileService = fileService;
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
            @RequestPart("file") MultipartFile file,
            @RequestParam("userId") Long userId
    ) {
        fileService.identifyUser(userId);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/arquivos")
    public ResponseEntity<List<FileResponseDTO>> listFiles(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(datasetService.listFiles(id));
    }
}