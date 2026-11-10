package catdevs.georuraldatahub.service;

import catdevs.georuraldatahub.collection.CollectedData;
import catdevs.georuraldatahub.processing.GeospatialReader;
import catdevs.georuraldatahub.validation.RecordValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Mesma conversão para inferir o dicionário e para validar a ingestão. */
final class IngestionContent {
    private static final ObjectMapper mapper = new ObjectMapper();
    private IngestionContent() { }
    record Prepared(GeospatialReader.Result content, List<RecordValidationResult> errors) { }
    static Prepared prepare(MultipartFile multipartFile, String format, CollectedData collected,
            GeospatialReader geospatial) throws Exception {
        catdevs.georuraldatahub.processing.GeospatialReader.Result prepared = null;
        List<RecordValidationResult> preparationErrors = null;
        boolean raster = java.util.Set.of("tif", "tiff").contains(format);
        int sourceSrid = 0;
        boolean declaredCrs = false;
        if (collected != null && collected.metadata() != null
                && "shapefile.zip".equals(collected.metadata().originFormat())
                && java.util.Set.of("IBGE_BIOMAS", "IBGE_MUNICIPIOS").contains(collected.metadata().product())) {
            // O leitor IBGE já verificou SIRGAS 2000 geográfico; converter para GeoJSON não reprojeta o datum.
            sourceSrid = 4674;
        } else if (java.util.Set.of("geojson", "json").contains(format)) {
            try (InputStream input = multipartFile.getInputStream()) {
                var root = mapper.readTree(input);
                declaredCrs = root != null && root.hasNonNull("crs")
                        && java.util.Set.of("FeatureCollection", "Feature").contains(root.path("type").asText());
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // O validador abaixo registra o erro estrutural e preserva o original na quarentena.
            }
        }
        if (raster || java.util.Set.of("zip", "gpkg").contains(format) || sourceSrid != 0 || declaredCrs) {
            try (InputStream input = multipartFile.getInputStream()) {
                prepared = geospatial.read(input, format.equals("json") ? "geojson" : format, sourceSrid);
            } catch (ResponseStatusException e) {
                if (e.getStatusCode().value() != 422) throw e;
                preparationErrors = List.of(RecordValidationResult.invalid(-1, "",
                        List.of("INVALID_GEOSPATIAL_FILE: " + e.getReason())));
            }
        }
        return new Prepared(prepared, preparationErrors);
    }
}
