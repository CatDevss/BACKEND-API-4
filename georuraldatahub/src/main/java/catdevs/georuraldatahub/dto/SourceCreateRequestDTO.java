package catdevs.georuraldatahub.dto;

public record SourceCreateRequestDTO(
        String name,
        String url,
        Long userId
) {
}