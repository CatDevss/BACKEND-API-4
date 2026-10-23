package catdevs.georuraldatahub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SourceCreateRequestDTO(

    @NotBlank(message = "Nome da fonte é obrigatório") @Size(max = 150) 
    String name,

    @NotBlank(message = "URL da fonte é obrigatória") @Size(max = 2048) 
    String url,

    @NotNull(message = "Usuário é obrigatório") 
    Long userId,

    @Size(max = 1000) 
    String description
    
) {}
