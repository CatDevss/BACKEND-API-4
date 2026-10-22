package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import catdevs.georuraldatahub.collection.CollectionRequest.Product;
import catdevs.georuraldatahub.validation.*;

/** Opt-in integration checks against the actual public services; no Oracle or OCI writes.
 * Run: mvn -Dtest=LiveCollectionTest -Dcollection.live.tests=true test */
@EnabledIfSystemProperty(named="collection.live.tests",matches="true")
class LiveCollectionTest {
    private final CollectionProperties limits = new CollectionProperties();
    private final PublicDataConnectors connectors = new PublicDataConnectors(new OfficialDataClient(limits),
            new BiomesShapefileReader(limits),limits,new CollectionCatalog());
    private final ObjectMapper mapper = new ObjectMapper();
    private CollectionRequest request(Product p,String competence,String municipality,String biome,List<Double> bbox) {
        return new CollectionRequest(p,1L,2L,competence,municipality,biome,bbox);
    }
    @Test void downloadsMunicipalityFromIbge() throws Exception {
        var result=connectors.collect(request(Product.IBGE_MUNICIPIOS,"2022","4106902",null,null));
        assertEquals(1,mapper.readTree(result.content()).path("features").size());
        assertArrayEquals(result.content(),result.originals().getFirst().content());
        assertValid(result, List.of(FieldRule.required("codarea", FieldType.STRING)));
    }
    @Test void downloadsParanaMunicipalMesh() throws Exception {
        var result=connectors.collect(request(Product.IBGE_MUNICIPIOS,"2022",null,null,null));
        assertEquals(399,mapper.readTree(result.content()).path("features").size());
        assertValid(result, List.of(FieldRule.required("codarea", FieldType.STRING)));
    }
    @Test void downloads2024MunicipalArchiveForParanaAndFiltersCuritiba() throws Exception {
        var result = connectors.collect(request(Product.IBGE_MUNICIPIOS, "2024", null, null, null));
        assertEquals(399, mapper.readTree(result.content()).path("features").size());
        assertEquals("shapefile.zip", result.metadata().originFormat());
        assertTrue(result.originals().getFirst().origin().endsWith("PR_Municipios_2024.zip"));
        assertValid(result, new CollectionCatalog().dictionaryTemplate(Product.IBGE_MUNICIPIOS, "2024").rules());
        var city = connectors.collect(request(Product.IBGE_MUNICIPIOS, "2024", "4106902", null, null));
        var features = mapper.readTree(city.content()).path("features");
        assertEquals(1, features.size());
        var properties = features.get(0).path("properties");
        assertEquals("4106902", properties.path("codarea").asText());
        assertEquals("4106902", properties.path("CD_MUN").asText());
        assertEquals("Curitiba", properties.path("NM_MUN").asText());
        assertEquals("PR", properties.path("SIGLA_UF").asText());
        assertTrue(properties.path("AREA_KM2").isNumber());
        assertEquals(16, properties.size());
        assertValid(city, new CollectionCatalog().dictionaryTemplate(Product.IBGE_MUNICIPIOS, "2024").rules());
    }

    @Test void downloadsAndConverts2019Biomes() throws Exception { biomes("2019"); }
    @Test void downloadsAndConverts2025Biomes() throws Exception { biomes("2025"); }
    private void biomes(String edition) throws Exception {
        var result=connectors.collect(request(Product.IBGE_BIOMAS,edition,null,null,null));
        assertTrue(mapper.readTree(result.content()).path("features").size() >= 1);
        assertTrue(result.metadata().scope().contains("Paraná"));
        assertTrue(result.originals().getFirst().content().length>100000);
        assertEquals("shapefile.zip",result.metadata().originFormat());
        assertValid(result, edition.equals("2019")
                ? List.of(FieldRule.required("Bioma", FieldType.STRING), FieldRule.required("CD_Bioma", FieldType.NUMBER))
                : List.of(FieldRule.required("CD_BIOMA", FieldType.STRING), FieldRule.required("NM_BIOMA", FieldType.STRING)));
    }
    @Test void downloadsAllProdesPagesWithinSmallBbox() throws Exception {
        limits.setPageSize(1);limits.setMaxFeatures(50);
        var result=connectors.collect(request(Product.INPE_PRODES,"2023",null,"MATA_ATLANTICA",List.of(-52.756,-23.346,-52.752,-23.342)));
        var features=mapper.readTree(result.content()).path("features");
        assertTrue(features.size()>0);
        assertEquals(features.size()+1,result.originals().size());
        for(var f:features)assertEquals(2023,f.path("properties").path("year").asInt());
        assertValid(result, new CollectionCatalog().dictionaryTemplate(Product.INPE_PRODES, "2023").rules());
    }
    @Test void downloadsCompletedDailyFireFile() throws Exception {
        String date=LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(2).toString();
        var result=connectors.collect(request(Product.INPE_QUEIMADAS,date,null,null,null));
        assertTrue(result.originals().getFirst().content().length>100);
        assertTrue(result.metadata().scope().contains("Paraná"));
        assertEquals("csv",result.format());
        var rules = new CollectionCatalog().dictionaryTemplate(Product.INPE_QUEIMADAS, date).rules();
        // A day with no PR detections is a legitimate empty result, not proof of a broken channel.
        try (var reader = new CsvRecordValidator.TableReader(new java.io.ByteArrayInputStream(result.content()))) {
            reader.readRecord();
            if (reader.readRecord() != null) assertValid(result, rules);
        }
    }
    @Test void downloadsAndValidatesCerradoWithinParanaBbox() throws Exception {
        var result = connectors.collect(request(Product.INPE_PRODES, "2023", null, "CERRADO", List.of(-50.4, -25., -49.5, -24.)));
        assertTrue(mapper.readTree(result.content()).path("features").size() > 0);
        assertValid(result, new CollectionCatalog().dictionaryTemplate(Product.INPE_PRODES, "2023").rules());
    }

    private void assertValid(CollectedData result, List<FieldRule> rules) throws Exception {
        var validator = new FileValidationService(new CsvRecordValidator(), new GeoJsonRecordValidator());
        var records = validator.validate(new java.io.ByteArrayInputStream(result.content()), result.format(), rules);
        var report = FileValidationReport.of(1L, 1, records);
        assertEquals("VALIDO", report.status(), () -> result.filename() + ": " + records.stream()
                .filter(record -> !record.valid()).limit(3).toList());
        byte[] accepted = validator.acceptedContent(new java.io.ByteArrayInputStream(result.content()), result.format(), records);
        assertTrue(accepted.length > 0);
    }

}
