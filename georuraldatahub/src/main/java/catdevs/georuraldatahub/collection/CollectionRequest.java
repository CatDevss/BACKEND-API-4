package catdevs.georuraldatahub.collection;

import java.util.List;
import jakarta.validation.constraints.*;

public record CollectionRequest(@NotNull Product product, @Positive Long datasetId,
        @NotNull @Positive Long userId, @NotBlank String competence,
        String municipality, String biome, List<Double> bbox) {
    public enum Product { IBGE_MUNICIPIOS, IBGE_BIOMAS, INPE_PRODES, INPE_QUEIMADAS }
}
