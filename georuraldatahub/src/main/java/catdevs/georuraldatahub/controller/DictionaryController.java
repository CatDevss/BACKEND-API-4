package catdevs.georuraldatahub.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import catdevs.georuraldatahub.dto.*;
import catdevs.georuraldatahub.service.DictionaryService;

@RestController
@CrossOrigin
public class DictionaryController {
    private final DictionaryService service;
    public DictionaryController(DictionaryService service) { this.service = service; }

    @PostMapping("/fontes/{sourceId}/dicionarios")
    @ResponseStatus(HttpStatus.CREATED)
    public DictionaryResponseDTO create(@PathVariable Long sourceId, @Valid @RequestBody DictionaryRequestDTO request) {
        return service.create(sourceId, request);
    }
    @GetMapping("/fontes/{sourceId}/dicionarios")
    public List<DictionaryResponseDTO> list(@PathVariable Long sourceId) { return service.list(sourceId); }
    @GetMapping("/dicionarios/{id}")
    public DictionaryResponseDTO get(@PathVariable Long id) { return service.get(id); }
    @PutMapping("/dicionarios/{id}")
    public DictionaryResponseDTO update(@PathVariable Long id, @Valid @RequestBody DictionaryRequestDTO request) {
        return service.update(id, request);
    }
    @DeleteMapping("/dicionarios/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
    @PostMapping("/dicionarios/{id}/versoes")
    @ResponseStatus(HttpStatus.CREATED)
    public DictionaryResponseDTO newVersion(@PathVariable Long id) { return service.newVersion(id); }
}
