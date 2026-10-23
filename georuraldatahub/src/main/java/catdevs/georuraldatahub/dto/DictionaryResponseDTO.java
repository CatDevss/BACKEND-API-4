package catdevs.georuraldatahub.dto;

import java.time.LocalDateTime;
import java.util.List;
import catdevs.georuraldatahub.validation.FieldRule;

public record DictionaryResponseDTO(Long id, Long sourceId, String name, String description,
        Integer version, String status, List<FieldRule> rules, LocalDateTime createdOn) {}
