package catdevs.georuraldatahub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SourceCreateRequestDTO(

    @NotBlank(message = "Nome da fonte é obrigatório")
    String name,

    @NotBlank(message = "URL da fonte é obrigatória")
    String url,

    @NotNull(message = "Usuário é obrigatório")
    Long userId

) {}