package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import catdevs.georuraldatahub.collection.CollectionRequest.Product;

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
    }
    @Test void downloadsParanaMunicipalMesh() throws Exception {
        var result=connectors.collect(request(Product.IBGE_MUNICIPIOS,"2022",null,null,null));
        assertEquals(399,mapper.readTree(result.content()).path("features").size());
    }
    @Test void downloadsAndConverts2019Biomes() throws Exception { biomes("2019"); }
    @Test void downloadsAndConverts2025Biomes() throws Exception { biomes("2025"); }
    private void biomes(String edition) throws Exception {
        var result=connectors.collect(request(Product.IBGE_BIOMAS,edition,null,null,null));
        assertTrue(mapper.readTree(result.content()).path("features").size() >= 1);
        assertTrue(result.metadata().scope().contains("Paraná"));
        assertTrue(result.originals().getFirst().content().length>100000);
        assertEquals("shapefile.zip",result.metadata().originFormat());
    }
    @Test void downloadsAllProdesPagesWithinSmallBbox() throws Exception {
        limits.setPageSize(1);limits.setMaxFeatures(50);
        var result=connectors.collect(request(Product.INPE_PRODES,"2023",null,"MATA_ATLANTICA",List.of(-52.756,-23.346,-52.752,-23.342)));
        var features=mapper.readTree(result.content()).path("features");
        assertTrue(features.size()>0);
        assertEquals(features.size()+1,result.originals().size());
        for(var f:features)assertEquals(2023,f.path("properties").path("year").asInt());
    }
    @Test void downloadsCompletedDailyFireFile() throws Exception {
        String date=LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(2).toString();
        var result=connectors.collect(request(Product.INPE_QUEIMADAS,date,null,null,null));
        assertTrue(result.originals().getFirst().content().length>100);
        assertTrue(result.metadata().scope().contains("Paraná"));
        assertEquals("csv",result.format());
    }
}
