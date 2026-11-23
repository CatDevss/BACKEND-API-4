package catdevs.georuraldatahub.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import catdevs.georuraldatahub.security.Permissions;

import catdevs.georuraldatahub.dto.DatasetStructureDTO;
import catdevs.georuraldatahub.service.DatasetStructureService;
import org.springframework.web.bind.annotation.*;

@RestController
public class DatasetStructureController {
    private final DatasetStructureService structures;

    public DatasetStructureController(DatasetStructureService structures) {
        this.structures = structures;
    }

    @PreAuthorize(Permissions.DATASETS_READ)
    @GetMapping("/conjuntos/{id}/estrutura")
    public DatasetStructureDTO get(@PathVariable Long id) {
        return structures.get(id);
    }
}
