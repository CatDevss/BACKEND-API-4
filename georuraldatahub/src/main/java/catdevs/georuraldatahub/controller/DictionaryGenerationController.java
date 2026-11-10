package catdevs.georuraldatahub.controller;

import catdevs.georuraldatahub.collection.CollectionRequest;
import catdevs.georuraldatahub.service.DictionaryGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin
@RequestMapping("/fontes/{sourceId}/dicionarios")
public class DictionaryGenerationController {
    private final DictionaryGenerationService service;
    public DictionaryGenerationController(DictionaryGenerationService service) { this.service = service; }

    @PostMapping(value = "/gerar-arquivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DictionaryGenerationService.Result upload(@PathVariable Long sourceId, @RequestParam Long userId,
            @RequestPart("file") MultipartFile file) throws Exception {
        return service.fromUpload(sourceId, userId, file);
    }

    @PostMapping("/gerar-coleta")
    @ResponseStatus(HttpStatus.CREATED)
    public DictionaryGenerationService.Result collect(@PathVariable Long sourceId,
            @Valid @RequestBody CollectionRequest request) throws Exception {
        return service.fromCollection(sourceId, request);
    }
}
