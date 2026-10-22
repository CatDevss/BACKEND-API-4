package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.dto.DatasetStructureDTO;
import catdevs.georuraldatahub.service.DatasetStructureService;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin
public class DatasetStructureController {
    private final DatasetStructureService structures;

    public DatasetStructureController(DatasetStructureService structures) { this.structures = structures; }

    @GetMapping("/conjuntos/{id}/estrutura")
    public DatasetStructureDTO get(@PathVariable Long id) { return structures.get(id); }
}
