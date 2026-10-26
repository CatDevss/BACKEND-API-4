package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import catdevs.georuraldatahub.validation.*;

class CollectionDictionaryTemplateTest {
    private final CollectionCatalog catalog = new CollectionCatalog();
    @Test void prodesTemplateAcceptsCurrentOfficialPublicationMetadataAndBothParanaNames() throws Exception {
        var template = catalog.dictionaryTemplate(CollectionRequest.Product.INPE_PRODES, "2023");
        var names = template.rules().stream().map(FieldRule::name).toList();
        assertTrue(names.containsAll(List.of("update_at", "pub_date", "publish_year")));
        assertEquals(names.size(), new HashSet<>(names).size());
        var validator = new FileValidationService(new CsvRecordValidator(), new GeoJsonRecordValidator());
        try (var input = getClass().getResourceAsStream("/collection/inpe-prodes-page.geojson")) {
            var rows = validator.validate(input, "geojson", template.rules());
            assertTrue(rows.stream().allMatch(RecordValidationResult::valid));
        }
    }
    @Test void biomeEditionsUseTheirOwnFieldNamesAndTypes() {
        var old = catalog.dictionaryTemplate(CollectionRequest.Product.IBGE_BIOMAS, "2019");
        var current = catalog.dictionaryTemplate(CollectionRequest.Product.IBGE_BIOMAS, "2025");
        assertTrue(old.rules().stream().anyMatch(rule -> rule.name().equals("CD_Bioma") && rule.type() == FieldType.NUMBER));
        assertTrue(current.rules().stream().anyMatch(rule -> rule.name().equals("CD_BIOMA") && rule.type() == FieldType.STRING));
        assertThrows(IllegalArgumentException.class, () -> catalog.dictionaryTemplate(CollectionRequest.Product.IBGE_BIOMAS, null));
    }
}
