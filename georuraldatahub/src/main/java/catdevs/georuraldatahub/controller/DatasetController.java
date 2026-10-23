
package catdevs.georuraldatahub.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import catdevs.georuraldatahub.dto.DatasetCreateRequestDTO;
import catdevs.georuraldatahub.dto.DatasetResponseDTO;
import catdevs.georuraldatahub.dto.DatasetUpdateRequestDTO;
import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.service.DatasetService;
import catdevs.georuraldatahub.service.FileService;
import jakarta.validation.Valid;

@RestController
@CrossOrigin
@RequestMapping("/conjuntos")
public class DatasetController {

        private final DatasetService datasetService;
        private final FileService fileService;

        public DatasetController(
                        DatasetService datasetService,
                        FileService fileService) {
                this.datasetService = datasetService;
                this.fileService = fileService;
        }

        @PostMapping
        public ResponseEntity<DatasetResponseDTO> create(
                        @Valid @RequestBody DatasetCreateRequestDTO request) {

                DatasetResponseDTO response = datasetService.create(request);

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @GetMapping
        public ResponseEntity<List<DatasetResponseDTO>> listAll() {
                return ResponseEntity.ok(datasetService.listAll());
        }

        @GetMapping("/{id}")
        public ResponseEntity<DatasetResponseDTO> findById(
                        @PathVariable Long id) {

                return ResponseEntity.ok(datasetService.findById(id));
        }

        @PutMapping("/{id}")
        public ResponseEntity<DatasetResponseDTO> update(
                        @PathVariable Long id,
                        @Valid @RequestBody DatasetUpdateRequestDTO request) {

                return ResponseEntity.ok(datasetService.update(id, request));
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> delete(@PathVariable Long id) {

                datasetService.delete(id);

                return ResponseEntity.noContent().build();
        }

        @PostMapping(value = "/{id}/arquivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<FileResponseDTO> uploadFile(
                        @PathVariable Long id,
                        @RequestPart("file") MultipartFile file,
                        @RequestParam("userId") Long userId,
                        @RequestParam("sourceId") Long sourceId) throws Exception {

                FileResponseDTO response = fileService.uploadFile(file, id, userId, sourceId);

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @GetMapping("/{id}/arquivos")
        public ResponseEntity<List<FileResponseDTO>> listFiles(
                        @PathVariable Long id) {

                return ResponseEntity.ok(fileService.findByDatasetId(id));
        }
}
