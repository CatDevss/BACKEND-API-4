package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import catdevs.georuraldatahub.collection.CollectionRequest.Product;

/** Downloads reais, sem gravação em banco ou bucket; falhas externas constam no relatório. */
@EnabledIfSystemProperty(named = "collection.published.live", matches = "true")
class LivePublishedCollectionTest {
    @Test void verifiesEveryPublishedSourceAndEdition() throws Exception {
        var limits = new CollectionProperties();
        limits.setMaxDownloadBytes(268435456);
        limits.setMaxTotalBytes(536870912);
        limits.setMaxDurationSeconds(900);
        limits.setRequestTimeoutSeconds(300);
        var http = new OfficialDataClient(limits);
        var connectors = new PublicDataConnectors(http, new BiomesShapefileReader(limits), limits, new CollectionCatalog());
        var resources = new PublishedResources(http);
        var mapper = new ObjectMapper();
        var checks = new ArrayList<Map<String, Object>>();
        var reportPath = Path.of("target/source-audit/published-" + System.currentTimeMillis() + ".json");
        for (Product product : List.of(Product.ICMBIO, Product.ICMBIO_EMBARGOS, Product.MMA, Product.FUNAI, Product.INCRA, Product.SFB, Product.IBAMA)) {
            if (!Arrays.asList(System.getProperty("collection.products", "ICMBIO,ICMBIO_EMBARGOS,MMA,FUNAI,INCRA,SFB,IBAMA").split(",")).contains(product.name())) continue;
            var editions = new ArrayList<String>();
            if (product != Product.SFB) editions.add("ATUAL");
            if (product == Product.MMA || product == Product.SFB)
                resources.editions(product).forEach(e -> editions.add(e.competence()));
            for (String edition : editions) {
                if (!Arrays.asList(System.getProperty("collection.editions", edition).split(",")).contains(edition)) continue;
                var check = new LinkedHashMap<String, Object>();
                check.put("product", product); check.put("competence", edition);
                long start = System.nanoTime();
                try {
                    var result = connectors.collect(new CollectionRequest(product, null, null, edition, null, null, null));
                    var root = mapper.readTree(result.content());
                    assertEquals("FeatureCollection", root.path("type").asText());
                    assertEquals(edition, result.metadata().competence());
                    check.put("status", "OK"); check.put("features", root.path("features").size());
                    check.put("bytes", result.content().length);
                    check.put("originals", result.metadata().originals());
                } catch (Exception | AssertionError e) {
                    check.put("status", "ERROR"); check.put("reason", e.toString());
                }
                check.put("seconds", (System.nanoTime() - start) / 1_000_000_000.0);
                checks.add(check);
                Files.createDirectories(Path.of("target/source-audit"));
                mapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), checks);
                System.out.println("SOURCE_AUDIT " + product + " " + edition + " " + check.get("status") + " " + check.getOrDefault("reason", check.get("features")));
            }
        }
        long failed = checks.stream().filter(c -> c.get("status").equals("ERROR")).count();
        assertEquals(0, failed, "Consulte " + reportPath + " para indisponibilidades e conteúdo incompatível.");
    }
}
