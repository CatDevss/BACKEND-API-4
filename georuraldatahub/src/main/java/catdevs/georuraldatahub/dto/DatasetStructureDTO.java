package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Explorador de consulta; referências apontam para arquivos tratados disponíveis para visualização. */
public record DatasetStructureDTO(Long id, String name, Long userId, List<SourceEntry> sources) {
    public record SourceEntry(Long id, String name, String url, List<FileEntry> files) {}
    public record FileEntry(Long id, String name, String formatFile, Long size, LocalDateTime receivedDate,
            Long ingestionId, Long dictionaryId, Integer dictionaryVersion, List<LayerInfoDTO> layers) {}
}
