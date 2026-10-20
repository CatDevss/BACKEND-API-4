package catdevs.georuraldatahub.controller;

import java.util.List;
import catdevs.georuraldatahub.dto.FileResponseDTO;
import catdevs.georuraldatahub.service.FileService;
import catdevs.georuraldatahub.service.SourceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin
@RequestMapping("/fontes/{sourceId}/arquivos")
public class SourceFileController {
    private final FileService files;
    private final SourceService sources;

    public SourceFileController(FileService files, SourceService sources) {
        this.files = files;
        this.sources = sources;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponseDTO upload(@PathVariable Long sourceId, @RequestParam Long userId,
            @RequestPart("file") MultipartFile file) throws Exception {
        return files.uploadToSource(file, sourceId, userId);
    }

    @GetMapping
    public List<FileResponseDTO> list(@PathVariable Long sourceId) {
        sources.findEntityById(sourceId);
        return files.findBySourceId(sourceId);
    }
}
