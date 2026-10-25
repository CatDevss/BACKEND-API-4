package catdevs.georuraldatahub.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import catdevs.georuraldatahub.dto.FileStageDTO;
import catdevs.georuraldatahub.service.FileStageService;

@RestController
@CrossOrigin
@RequestMapping("/arquivos")
public class FileStageController {

    private final FileStageService service;

    public FileStageController(FileStageService service) {
        this.service = service;
    }

    @GetMapping("/etapas")
    public List<FileStageDTO> stages() {
        return service.currentStages();
    }
}
