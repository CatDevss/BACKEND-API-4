package catdevs.georuraldatahub.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import catdevs.georuraldatahub.security.Permissions;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import catdevs.georuraldatahub.collection.*;
import catdevs.georuraldatahub.service.FileService;

@RestController
public class CollectionController {
    private final CollectionCatalog catalog;
    private final CollectionService collection;
    private final FileService files;

    public CollectionController(CollectionCatalog catalog, CollectionService collection, FileService files) {
        this.catalog = catalog;
        this.collection = collection;
        this.files = files;
    }

    @PreAuthorize(Permissions.SOURCES_READ)
    @GetMapping("/fontes/conectores")
    public List<CollectionCatalog.Entry> catalog() {
        return catalog.list();
    }

    @PreAuthorize(Permissions.DICTIONARIES_READ)
    @GetMapping("/fontes/conectores/{product}/dicionario")
    public catdevs.georuraldatahub.dto.DictionaryRequestDTO dictionaryTemplate(
            @PathVariable CollectionRequest.Product product, @RequestParam(required = false) String competence) {
        return catalog.dictionaryTemplate(product, competence);
    }

    @PreAuthorize(Permissions.INGESTION_RUN)
    @PostMapping("/fontes/{sourceId}/coletas")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionService.Result collect(@PathVariable Long sourceId, @Valid @RequestBody CollectionRequest request)
            throws Exception {
        return collection.collect(sourceId, request);
    }

    @PreAuthorize(Permissions.FILES_READ)
    @GetMapping("/arquivos/{fileId}/coleta")
    public CollectedData.Metadata metadata(@PathVariable Long fileId) throws Exception {
        return files.collectionMetadata(fileId);
    }
}
