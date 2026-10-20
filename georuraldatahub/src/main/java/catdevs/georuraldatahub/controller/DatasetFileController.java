package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.service.DatasetFileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin
@RequestMapping("/conjuntos/{datasetId}/arquivos/{fileId}")
public class DatasetFileController {
    private final DatasetFileService membership;

    public DatasetFileController(DatasetFileService membership) { this.membership = membership; }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void associate(@PathVariable Long datasetId, @PathVariable Long fileId) {
        membership.associate(datasetId, fileId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dissociate(@PathVariable Long datasetId, @PathVariable Long fileId) {
        membership.dissociate(datasetId, fileId);
    }
}
