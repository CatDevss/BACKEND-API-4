package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import catdevs.georuraldatahub.collection.CollectionRequest.Product;
import catdevs.georuraldatahub.validation.CsvRecordValidator;

/** Auditoria opt-in: amostra PRODES por ano/bioma e conversão de cada CSV diário retido. */
@EnabledIfSystemProperty(named = "collection.inpe.live", matches = "true")
class LiveInpeEditionsTest {
    private final CollectionProperties limits = new CollectionProperties();
    private final OfficialDataClient http = new OfficialDataClient(limits);
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Map<String,Object>> checks = new ArrayList<>();

    @Test void verifiesProdesYearFiltersAndEveryRetainedCompletedDay() throws Exception {
        var report = Path.of("target/source-audit/inpe-" + System.currentTimeMillis() + ".json");
        int lastYear = LocalDate.now(ZoneId.of("America/Sao_Paulo")).getYear();
        for (String biome : List.of("CERRADO", "MATA_ATLANTICA")) {
            String layer = "prodes-" + biome.toLowerCase(Locale.ROOT).replace('_','-') + "-nb:yearly_deforestation";
            for (int year = 2000; year <= lastYear; year++) {
                var check = check("INPE_PRODES", Integer.toString(year));
                check.put("biome", biome); check.put("method", "Uma feição e total declarado para PR; não é download integral do ano");
                String url = "https://terrabrasilis.dpi.inpe.br/geoserver/ows?service=WFS&version=2.0.0&request=GetFeature&typeNames="
                        + PublishedResources.encode(layer) + "&outputFormat=application%2Fjson&srsName=CRS%3A84&sortBy=uuid&count=1&CQL_FILTER="
                        + PublishedResources.encode("year=" + year + " AND state IN ('PR','PARANA','PARANÁ')");
                check.put("url", url);
                try (var budget = http.collectionBudget()) {
                    var page = mapper.readTree(http.download(URI.create(url)));
                    assertEquals("FeatureCollection",page.path("type").asText());
                    long total = page.path("numberMatched").asLong(page.path("totalFeatures").asLong(-1));
                    assertTrue(total >= 0);
                    assertEquals(total > 0 ? 1 : 0, page.path("features").size());
                    for (var feature : page.path("features")) {
                        assertEquals(year, feature.path("properties").path("year").asInt());
                        assertTrue(Set.of("PR","PARANA","PARANÁ").contains(feature.path("properties").path("state").asText()));
                        assertTrue(feature.path("geometry").isObject());
                    }
                    check.put("status",total > 0 ? "OK" : "SEM_REGISTROS"); check.put("matched",total);
                } catch (Exception | AssertionError e) { failure(check,e); }
                save(report,check);
            }
        }
        var connectors = new PublicDataConnectors(http,new BiomesShapefileReader(limits),limits,new CollectionCatalog());
        for (var edition : new PublishedResources(http).availability(Product.INPE_QUEIMADAS).editions()) {
            var check = check("INPE_QUEIMADAS",edition.competence());
            try {
                var data = connectors.collect(new CollectionRequest(Product.INPE_QUEIMADAS,null,null,edition.competence(),null,null,null));
                long records = 0;
                try (var reader = new CsvRecordValidator.TableReader(new java.io.ByteArrayInputStream(data.content()))) {
                    assertNotNull(reader.readRecord());
                    while(reader.readRecord() != null) records++;
                }
                check.put("status",records > 0 ? "OK" : "SEM_REGISTROS"); check.put("records",records);
                check.put("originals",data.metadata().originals());
            } catch (Exception | AssertionError e) { failure(check,e); }
            save(report,check);
        }
        assertEquals(0,checks.stream().filter(c -> "ERROR".equals(c.get("status"))).count(),"Consulte " + report);
    }
    private Map<String,Object> check(String product,String competence) {
        var check = new LinkedHashMap<String,Object>();
        check.put("product",product); check.put("competence",competence); check.put("checkedAt",Instant.now().toString());
        return check;
    }
    private void failure(Map<String,Object> check,Throwable error) {check.put("status","ERROR");check.put("reason",error.toString());}
    private void save(Path report,Map<String,Object> check) throws Exception {
        checks.add(check); Files.createDirectories(report.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(report.toFile(),checks);
        System.out.println("INPE_AUDIT " + check.get("product") + " " + check.get("competence") + " " + check.getOrDefault("biome","") + " " + check.get("status"));
    }
}
