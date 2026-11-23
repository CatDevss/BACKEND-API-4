package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import catdevs.georuraldatahub.collection.CollectionRequest.Product;

@EnabledIfSystemProperty(named = "collection.ibge.live", matches = "true")
class LiveIbgeEditionsTest {
    @Test void verifiesEveryPublishedMunicipalYear() throws Exception {
        var limits = new CollectionProperties();
        var http = new OfficialDataClient(limits);
        var resources = new PublishedResources(http);
        var connectors = new PublicDataConnectors(http,new BiomesShapefileReader(limits),limits,new CollectionCatalog());
        var mapper = new ObjectMapper();
        var checks = new ArrayList<Map<String,Object>>();
        var report = Path.of("target/source-audit/ibge-" + System.currentTimeMillis() + ".json");
        for (var edition : resources.availability(Product.IBGE_MUNICIPIOS).editions()) {
            if (!Arrays.asList(System.getProperty("collection.editions",edition.competence()).split(",")).contains(edition.competence())) continue;
            var check = new LinkedHashMap<String,Object>();check.put("year",edition.competence());
            try {
                var result = connectors.collect(new CollectionRequest(Product.IBGE_MUNICIPIOS,null,null,edition.competence(),null,null,null));
                var features = mapper.readTree(result.content()).path("features");
                assertTrue(features.size() >= 399);
                check.put("status","OK");check.put("features",features.size());check.put("originals",result.metadata().originals());
            } catch (Exception | AssertionError e) {check.put("status","ERROR");check.put("reason",e.toString());}
            checks.add(check);Files.createDirectories(report.getParent());mapper.writerWithDefaultPrettyPrinter().writeValue(report.toFile(),checks);
            System.out.println("IBGE_AUDIT " + edition.competence() + " " + check.get("status") + " " + check.getOrDefault("reason",check.get("features")));
        }
        assertEquals(0,checks.stream().filter(c -> "ERROR".equals(c.get("status"))).count(),"Consulte "+report);
    }
}
