package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import catdevs.georuraldatahub.collection.CollectionRequest.Product;

class MunicipalArchiveConnectorTest {
    private final OfficialDataClient http = mock(OfficialDataClient.class);
    private final BiomesShapefileReader shapes = mock(BiomesShapefileReader.class);
    private final CollectionProperties limits = new CollectionProperties();
    private final PublicDataConnectors connectors = new PublicDataConnectors(http, shapes, limits, new CollectionCatalog());
    private final ObjectMapper mapper = new ObjectMapper();
    private final byte[] zip = new byte[]{1,2,3};

    @BeforeEach void setup() throws Exception { when(http.download(any())).thenReturn(zip); }
    private void mesh(String... codes) throws Exception {
        var root = mapper.createObjectNode().put("type", "FeatureCollection");
        var features = root.putArray("features");
        for (String code : codes) {
            var feature = features.addObject().put("type", "Feature");
            feature.putObject("properties").put("CD_MUN", code).put("NM_MUN", "Nome original")
                    .put("AREA_KM2", 123.456).putNull("CD_CONCU").put("CAMPO_NOVO", "Preservado");
            feature.set("geometry", mapper.readTree("{\"type\":\"Polygon\",\"coordinates\":[[[-49,-25],[-49.1,-25],[-49,-25.1],[-49,-25]]]}"));
        }
        when(shapes.toGeoJson(zip)).thenReturn(mapper.writeValueAsBytes(root));
    }
    private CollectionRequest request(String code) { return new CollectionRequest(Product.IBGE_MUNICIPIOS, null, 2L, "2024", code, null, null); }

    @Test void currentEditionPreservesAllSourceAttributesTypesAndLegacyCodeAlias() throws Exception {
        mesh("4106902", "4113700");
        var result = connectors.collect(request(null));
        verify(http).download(URI.create("https://geoftp.ibge.gov.br/organizacao_do_territorio/malhas_territoriais/malhas_municipais/municipio_2024/UFs/PR/PR_Municipios_2024.zip"));
        assertEquals("2024", result.metadata().competence());
        assertEquals("shapefile.zip", result.metadata().originFormat());
        assertArrayEquals(zip, result.originals().getFirst().content());
        var properties = mapper.readTree(result.content()).path("features").get(0).path("properties");
        var source = mapper.readTree(shapes.toGeoJson(zip)).path("features").get(0);
        var expected = source.path("properties").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected).put("codarea", "4106902");
        assertEquals(expected, properties);
        assertEquals(source.path("geometry"), mapper.readTree(result.content()).path("features").get(0).path("geometry"));
        assertEquals("Nome original", properties.path("NM_MUN").asText());
        assertTrue(properties.path("AREA_KM2").isNumber());
        assertTrue(properties.path("CD_CONCU").isNull());
        assertEquals("Preservado", properties.path("CAMPO_NOVO").asText());
    }
    @Test void individualMunicipalityIsSelectedFromTheSameEditionWithoutChangingPeriod() throws Exception {
        mesh("4106902", "4113700");
        var result = connectors.collect(request("4113700"));
        var features = mapper.readTree(result.content()).path("features");
        assertEquals(1, features.size()); assertEquals("4113700", features.get(0).path("properties").path("codarea").asText());
    }
    @Test void outsideStateOrDuplicateCodesAreRejectedRatherThanSilentlyFiltered() throws Exception {
        for (String code : List.of("3550308", "4106902")) {
            mesh("4106902", code);
            assertEquals(502, assertThrows(ResponseStatusException.class, () -> connectors.collect(request(null))).getStatusCode().value());
        }
    }
    @Test void missingMunicipalityReturns404InsteadOfPretendingToHaveCollectedAnotherCity() throws Exception {
        mesh("4113700", "4104808");
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> connectors.collect(request("4106902"))).getStatusCode().value());
    }
}
