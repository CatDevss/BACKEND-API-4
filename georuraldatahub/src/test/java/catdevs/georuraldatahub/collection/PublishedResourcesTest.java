package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import catdevs.georuraldatahub.collection.CollectionRequest.Product;

class PublishedResourcesTest {
    private final OfficialDataClient http = mock(OfficialDataClient.class);
    private final PublishedResources resources = new PublishedResources(http);
    private void reply(String json) throws Exception { when(http.download(any())).thenReturn(json.getBytes(StandardCharsets.UTF_8)); }

    @Test void annualSelectionUsesLatestPublishedEditionOfThatYearOnly() throws Exception {
        reply("""
            {"success":true,"result":{"resources":[
              {"name":"Polígono CNUC 2024_02","format":"ZIP + SHP","url":"https://dados.mma.gov.br/a.zip"},
              {"name":"Polígono CNUC 2025_03","format":"ZIP + SHP","url":"https://dados.mma.gov.br/b.zip"},
              {"name":"Polígono CNUC 2024_10","format":"ZIP + SHP","url":"https://dados.mma.gov.br/c.zip"},
              {"name":"CNUC 2024_12","format":"CSV","url":"https://dados.mma.gov.br/d.csv"}]}}
            """);
        assertEquals("2024-10", resources.resolve(Product.MMA, "2024").competence());
        assertEquals("2024-02", resources.resolve(Product.MMA, "2024-02").competence());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> resources.resolve(Product.MMA, "2023")).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> resources.resolve(Product.MMA, "2024-12")).getStatusCode().value());
    }

    @Test void discoveryDoesNotAuthorizeArbitraryDownloadHosts() throws Exception {
        reply("""
            {"success":true,"result":{"resources":[
              {"name":"CNUC 2024_02","format":"ZIP","url":"https://localhost/secrets.zip"}]}}
            """);
        assertThrows(IllegalArgumentException.class, () -> resources.resolve(Product.MMA, "2024"));
        verify(http).download(URI.create(PublishedResources.MMA_CATALOG));
    }

    @Test void snapshotProductsDoNotInventAnnualEditions() throws Exception {
        var result = resources.availability(Product.INCRA);
        assertEquals("BASE_ATUAL", result.temporalMode());
        assertEquals("ATUAL", result.editions().getFirst().competence());
        verify(http, never()).download(any());
    }

    @Test void fireDiscoveryIncludesOnlyRetainedCompletedDaysAndLocalFilenames() throws Exception {
        var today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        var yesterday = today.minusDays(1);
        var format = java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
        String html = "<a href='focos_diario_br_" + yesterday.format(format) + ".csv'>ontem</a>"
                + "<a href='focos_diario_br_" + today.format(format) + ".csv'>hoje</a>"
                + "<a href='https://example.com/focos_diario_br_20200101.csv'>externo</a>";
        when(http.listing(URI.create(PublishedResources.FIRES_DIRECTORY))).thenReturn(html.getBytes(StandardCharsets.UTF_8));
        var result = resources.availability(Product.INPE_QUEIMADAS);
        assertEquals(1,result.editions().size());
        assertEquals(yesterday.toString(),result.editions().getFirst().competence());
    }

    @Test void sfbResolvesPublicItemFromCatalogInsteadOfGuessingAnnualZipName() throws Exception {
        reply("""
            {"success":true,"result":{"resources":[
              {"name":"2024 - Cadastro retificado","format":"","url":"https://mapas.florestal.gov.br/portal/home/item.html?id=0123456789abcdef0123456789abcdef"},
              {"name":"2024 - Estatísticas","format":"CSV","url":"https://dados.florestal.gov.br/x.csv"}]}}
            """);
        var edition = resources.resolve(Product.SFB, "2024");
        assertTrue(edition.url().endsWith("/0123456789abcdef0123456789abcdef/data"));
        assertEquals(1, resources.editions(Product.SFB).size());
    }
}
