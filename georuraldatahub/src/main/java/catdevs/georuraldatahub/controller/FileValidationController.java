package catdevs.georuraldatahub.controller;

import org.springframework.web.bind.annotation.*;
import catdevs.georuraldatahub.service.FileService;
import catdevs.georuraldatahub.validation.FileValidationReport;

@RestController
@RequestMapping("/arquivos")
@CrossOrigin
public class FileValidationController {
    private final FileService service;
    public FileValidationController(FileService service) { this.service = service; }
    @GetMapping("/{id}/validacao")
    public FileValidationReport report(@PathVariable Long id) throws Exception { return service.validationReport(id); }
}
