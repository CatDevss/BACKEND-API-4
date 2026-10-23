package catdevs.georuraldatahub.dto; 

import jakarta.validation.constraints.NotNull; 
public record CollectionRequestDTO(
    @NotNull Long userId
){}
