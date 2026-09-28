package catdevs.georuraldatahub.validation;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class FileValidationService {

    private final CsvRecordValidator csvValidator;
    private final GeoJsonRecordValidator geoJsonValidator;

    public FileValidationService(CsvRecordValidator csvValidator, GeoJsonRecordValidator geoJsonValidator) {
        this.csvValidator = csvValidator;
        this.geoJsonValidator = geoJsonValidator;
    }

    public List<RecordValidationResult> validate(
            InputStream inputStream,
            String format,
            List<FieldRule> rules
    ) throws Exception {

        String normalizedFormat = format == null ? "" : format.toLowerCase(Locale.ROOT);

        return switch (normalizedFormat) {
            case "csv" -> csvValidator.validate(inputStream, rules);
            case "geojson", "json" -> geoJsonValidator.validate(inputStream, rules);
            default -> throw new IllegalArgumentException(
                    "Validação de conteúdo não suportada para o formato: " + format
            );
        };
    }
}