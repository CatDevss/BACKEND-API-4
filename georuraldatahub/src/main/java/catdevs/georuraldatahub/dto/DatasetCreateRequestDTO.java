package catdevs.georuraldatahub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DatasetCreateRequestDTO(

    @NotBlank(message = "Nome do conjunto é obrigatório")
    String name,

    @NotNull(message = "Fonte é obrigatória")
    Long sourceId

) {}