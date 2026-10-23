package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import catdevs.georuraldatahub.collection.CollectionRequest.Product;

class PublicDataConnectorsTest {
    private final OfficialDataClient http = mock(OfficialDataClient.class);
    private final CollectionProperties limits = new CollectionProperties();
    private final ObjectMapper mapper = new ObjectMapper();
    private final PublicDataConnectors connectors = new PublicDataConnectors(http, new BiomesShapefileReader(limits), limits, new CollectionCatalog());
    static byte[] fixture(String name) throws Exception {
        try (var input = PublicDataConnectorsTest.class.getResourceAsStream("/collection/" + name)) {
            assertNotNull(input); return input.readAllBytes();
        }
    }
    private static boolean boundaryUri(URI uri) {
        return uri != null && uri.getPath().endsWith("/estados/PR") && !uri.getQuery().contains("intrarregiao");
    }
    @BeforeEach void stateBoundary() throws Exception {
        when(http.download(argThat(PublicDataConnectorsTest::boundaryUri))).thenReturn(fixture("ibge-parana-2022.geojson"));
    }
    private CollectionRequest request(Product p, String period, String municipality, String biome, List<Double> bbox) {
        return new CollectionRequest(p, 1L, 2L, period, municipality, biome, bbox);
    }
    @Test void municipalResponseIsPreservedWithOriginAndHash() throws Exception {
        byte[] bytes = fixture("ibge-municipio.geojson"); when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn(bytes);
        var result = connectors.collect(request(Product.IBGE_MUNICIPIOS, "2022", "4106902", null, null));
        assertArrayEquals(bytes, result.content());
        assertEquals(64, result.metadata().originals().getFirst().sha256().length());
        assertTrue(result.metadata().originals().getFirst().origin().contains("periodo=2022"));
        assertTrue(result.metadata().scope().contains("4106902"));
    }
    @Test void nationalMunicipalMeshUsesMunicipalitySubdivision() throws Exception {
        ObjectNode mesh = (ObjectNode) mapper.readTree(fixture("ibge-municipio.geojson"));
        ObjectNode second = ((ObjectNode) mesh.path("features").get(0)).deepCopy();
        ((ObjectNode) second.path("properties")).put("codarea", "4113700");
        ((ArrayNode) mesh.path("features")).add(second);
        when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn(mapper.writeValueAsBytes(mesh));
        connectors.collect(request(Product.IBGE_MUNICIPIOS, "2022", null, null, null));
        verify(http).download(argThat(uri -> uri.toString().contains("/estados/PR") && uri.toString().contains("intrarregiao=municipio")));
    }
    @Test void rejectsInvalidParametersBeforeAnyDownload() {
        for (var r : List.of(request(Product.IBGE_MUNICIPIOS, "2022", "1/../../", null, null),
                request(Product.IBGE_MUNICIPIOS, "2022 OR 1=1", "4106902", null, null),
                request(Product.IBGE_BIOMAS, "2024", null, null, null),
                request(Product.INPE_PRODES, "2023", null, "INVALIDO", null),
                request(Product.IBGE_MUNICIPIOS, "2022", "3550308", null, null),
                request(Product.INPE_PRODES, "2023", null, "AMAZONIA", null),
                request(Product.INPE_PRODES, "2023", null, "CERRADO", List.of(4., 5., 1., 2.)),
                request(Product.INPE_QUEIMADAS, "2999-01-01", null, null, null)))
            assertThrows(IllegalArgumentException.class, () -> connectors.collect(r));
        verifyNoInteractions(http);
    }
    @Test void convertsRealBiomeArchiveAndRetainsOriginalBytes() throws Exception {
        byte[] zip = fixture("ibge-biomas-2004.zip"); when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn(zip);
        var result = connectors.collect(request(Product.IBGE_BIOMAS, "2019", null, null, null));
        assertEquals("FeatureCollection", mapper.readTree(result.content()).path("type").asText());
        assertTrue(mapper.readTree(result.content()).path("features").size() >= 1);
        assertArrayEquals(zip, result.originals().getFirst().content());
        assertEquals("shapefile.zip", result.metadata().originFormat());
    }
    @Test void recognizesRealDailyCsv() throws Exception {
        byte[] csv = fixture("inpe-focos-sample.csv"); when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn(csv);
        var result = connectors.collect(request(Product.INPE_QUEIMADAS, "2026-10-18", null, null, null));
        assertArrayEquals(csv, result.originals().getFirst().content());
        assertTrue(new String(result.content(), StandardCharsets.UTF_8).contains("\"41\""));
        assertTrue(result.filename().contains("20261018"));
    }
    @Test void rejectsHtmlInsteadOfData() throws Exception {
        when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn("<html>login</html>".getBytes(StandardCharsets.UTF_8));
        assertThrows(ResponseStatusException.class, () -> connectors.collect(request(Product.INPE_QUEIMADAS, "2026-10-18", null, null, null)));
        assertThrows(ResponseStatusException.class, () -> connectors.collect(request(Product.IBGE_MUNICIPIOS, "2022", null, null, null)));
    }
    private byte[] page(int matched, int returned) throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree(fixture("inpe-prodes-page.geojson"));
        root.put("numberMatched", matched); root.put("totalFeatures", matched);
        ArrayNode features = root.putArray("features");
        JsonNode real = mapper.readTree(fixture("inpe-prodes-page.geojson")).path("features");
        for (int i = 0; i < returned; i++) features.add(real.get(i));
        return mapper.writeValueAsBytes(root);
    }
    @Test void prodesCombinesAllPagesAndRecordsEveryOriginal() throws Exception {
        limits.setPageSize(1);
        byte[] first = page(2, 1);
        ObjectNode second = (ObjectNode) mapper.readTree(first);
        ((ObjectNode) second.path("features").get(0).path("properties")).put("uuid", "another-uuid");
        when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn(first, mapper.writeValueAsBytes(second));
        var result = connectors.collect(request(Product.INPE_PRODES, "2023", null, "CERRADO", List.of(-53., -24., -52., -23.)));
        assertEquals(3, result.originals().size());
        assertEquals(2, mapper.readTree(result.content()).path("features").size());
        verify(http).download(argThat(uri -> uri.toString().contains("startIndex=1") && uri.toString().contains("CQL_FILTER=year%3D2023")));
        assertTrue(result.metadata().scope().contains("bbox"));
    }
    @Test void repeatedPagesFailRatherThanPretendCollectionIsComplete() throws Exception {
        limits.setPageSize(1); when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn(page(2, 1));
        assertThrows(ResponseStatusException.class, () -> connectors.collect(request(Product.INPE_PRODES, "2023", null, "CERRADO", null)));
    }
    @Test void exceedingFeatureLimitDoesNotReturnTruncatedBase() throws Exception {
        limits.setMaxFeatures(1); when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn(page(2, 1));
        assertThrows(ResponseStatusException.class, () -> connectors.collect(request(Product.INPE_PRODES, "2023", null, "CERRADO", null)));
        verify(http, times(1)).download(argThat(uri -> !boundaryUri(uri)));
    }
    @Test void mismatchedYearAndChangingTotalsFail() throws Exception {
        when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn(page(1, 1));
        assertThrows(ResponseStatusException.class, () -> connectors.collect(request(Product.INPE_PRODES, "2022", null, "CERRADO", null)));
        limits.setPageSize(1); when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn(page(2, 1), page(3, 1));
        assertThrows(ResponseStatusException.class, () -> connectors.collect(request(Product.INPE_PRODES, "2023", null, "CERRADO", null)));
    }
    @Test void zeroFeaturesProducesAnExplicitEmptyCollection() throws Exception {
        when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn(page(0, 0));
        var result = connectors.collect(request(Product.INPE_PRODES, "2023", null, "CERRADO", null));
        assertEquals(0, mapper.readTree(result.content()).path("features").size());
    }
    @Test void refusesProdesResponseFromAnotherState() throws Exception {
        ObjectNode response = (ObjectNode) mapper.readTree(page(1,1));
        ((ObjectNode) response.path("features").get(0).path("properties")).put("state","SP");
        when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn(mapper.writeValueAsBytes(response));
        assertThrows(ResponseStatusException.class, () -> connectors.collect(request(Product.INPE_PRODES,"2023",null,"MATA_ATLANTICA",null)));
    }
    @Test void filtersOutFiresFromOtherStates() throws Exception {
        String csv="id,lat,lon,data_hora_gmt,satelite,estado_id,municipio_id\n"
                +"a,-25,-50,2026-10-18,A,41,4106902\n"
                +"b,-23,-46,2026-10-18,A,35,3550308\n";
        when(http.download(argThat(uri -> !boundaryUri(uri)))).thenReturn(csv.getBytes(StandardCharsets.UTF_8));
        var result=connectors.collect(request(Product.INPE_QUEIMADAS,"2026-10-18",null,null,null));
        String output=new String(result.content(),StandardCharsets.UTF_8);
        assertTrue(output.contains("4106902"));assertFalse(output.contains("3550308"));
        assertArrayEquals(csv.getBytes(StandardCharsets.UTF_8),result.originals().getFirst().content());
    }
    @Test void catalogKeepsUnapprovedProductsAndCarOnManualPath() {
        var entries = new CollectionCatalog().list();
        assertEquals(4, entries.stream().filter(e -> e.access().equals("AUTOMATICO")).count());
        assertEquals(6, entries.stream().filter(e -> e.access().equals("AGUARDA_HOMOLOGACAO")).count());
        assertEquals("MANUAL", entries.stream().filter(e -> e.id().equals("CAR_SICAR")).findFirst().orElseThrow().access());
    }
}
