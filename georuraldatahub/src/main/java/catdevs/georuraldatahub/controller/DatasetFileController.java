package catdevs.georuraldatahub.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import catdevs.georuraldatahub.security.Permissions;

import catdevs.georuraldatahub.service.DatasetFileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/conjuntos/{datasetId}/arquivos/{fileId}")
public class DatasetFileController {
    private final DatasetFileService membership;

    public DatasetFileController(DatasetFileService membership) {
        this.membership = membership;
    }

    @PreAuthorize(Permissions.DATASETS_MANAGE)
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void associate(@PathVariable Long datasetId, @PathVariable Long fileId) {
        membership.associate(datasetId, fileId);
    }

    @PreAuthorize(Permissions.DATASETS_MANAGE)
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dissociate(@PathVariable Long datasetId, @PathVariable Long fileId) {
        membership.dissociate(datasetId, fileId);
    }
}
