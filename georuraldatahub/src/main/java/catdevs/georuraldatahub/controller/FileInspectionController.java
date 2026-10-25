package catdevs.georuraldatahub.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import catdevs.georuraldatahub.service.FileInspectionService;

/**
 * Endpoints para consultar metadados, conteúdo textual paginado
 * e estrutura identificável de um arquivo.
 */
@RestController
@RequestMapping("/arquivos")
@CrossOrigin
public class FileInspectionController {

    private final FileInspectionService fileInspectionService;

    public FileInspectionController(FileInspectionService fileInspectionService) {
        this.fileInspectionService = fileInspectionService;
    }

    /**
     * Retorna os metadados do arquivo e informa se ele parece ser textual.
     * GET /arquivos/{id}/inspecao
     */
    @GetMapping("/{id}/inspecao")
    public Map<String, Object> getMetadata(
            @PathVariable("id") Long id) throws IOException {
        return fileInspectionService.getMetadata(id);
    }

    /**
     * Retorna uma página do conteúdo de um arquivo textual.
     * GET /arquivos/{id}/inspecao/conteudo?page=0&size=100
     */
    @GetMapping("/{id}/inspecao/conteudo")
    public Map<String, Object> getTextContent(
            @PathVariable("id") Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "100") int size)
            throws IOException {
        return fileInspectionService.getTextContent(id, page, size);
    }

    /**
     * Retorna a estrutura identificável do arquivo.
     * GET /arquivos/{id}/inspecao/estrutura
     */
    @GetMapping("/{id}/inspecao/estrutura")
    public Map<String, Object> getStructure(
            @PathVariable("id") Long id) throws IOException {
        return fileInspectionService.getStructure(id);
    }
}