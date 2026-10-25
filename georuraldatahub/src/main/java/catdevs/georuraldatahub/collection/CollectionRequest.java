package catdevs.georuraldatahub.collection;

import java.util.List;
import jakarta.validation.constraints.*;

public record CollectionRequest(@NotNull Product product, @Positive Long datasetId,
        @NotNull @Positive Long userId, @NotBlank String competence,
        String municipality, String biome, List<Double> bbox, @Positive Long dictionaryId) {
    public CollectionRequest(Product product, Long datasetId, Long userId, String competence,
            String municipality, String biome, List<Double> bbox) {
        this(product, datasetId, userId, competence, municipality, biome, bbox, null);
    }
    public enum Product { IBGE_MUNICIPIOS, IBGE_BIOMAS, INPE_PRODES, INPE_QUEIMADAS }
}
