package catdevs.georuraldatahub.dto;

import jakarta.validation.constraints.NotBlank;

public record DatasetUpdateRequestDTO(
        @NotBlank(message = "Nome do conjunto é obrigatório") String name) {
}