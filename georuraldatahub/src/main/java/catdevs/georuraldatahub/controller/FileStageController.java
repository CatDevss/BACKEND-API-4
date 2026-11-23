package catdevs.georuraldatahub.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import catdevs.georuraldatahub.security.Permissions;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import catdevs.georuraldatahub.dto.FileStageDTO;
import catdevs.georuraldatahub.service.FileStageService;

@RestController
@RequestMapping("/arquivos")
public class FileStageController {

    private final FileStageService service;

    public FileStageController(FileStageService service) {
        this.service = service;
    }

    /** Etapa atual de cada arquivo, usada pelos cards da tela de arquivos. */
    @PreAuthorize(Permissions.FILES_READ)
    @GetMapping("/etapas")
    public List<FileStageDTO> stages() {
        return service.currentStages();
    }
}
