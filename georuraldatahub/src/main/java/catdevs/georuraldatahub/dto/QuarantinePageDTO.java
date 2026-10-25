package catdevs.georuraldatahub.dto;

import java.util.List;

public record QuarantinePageDTO(List<QuarantineResponseDTO> content, int page, int size,
        long totalElements, int totalPages) {}
