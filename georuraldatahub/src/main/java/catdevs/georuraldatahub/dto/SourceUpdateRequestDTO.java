package catdevs.georuraldatahub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SourceUpdateRequestDTO(

        @NotBlank(message = "Nome da fonte é obrigatório")
        @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
        String name,

        @NotBlank(message = "URL da fonte é obrigatória")
        @Size(max = 200, message = "URL deve ter no máximo 200 caracteres")
        String url,

        @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
        String description

) {}

