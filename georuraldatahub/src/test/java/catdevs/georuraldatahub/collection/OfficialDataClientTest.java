package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OfficialDataClientTest {
    private final CollectionProperties limits = new CollectionProperties();
    private final HttpClient transport = mock(HttpClient.class);
    private final OfficialDataClient client = new OfficialDataClient(limits, transport);
    private final URI official = URI.create("https://servicodados.ibge.gov.br/api/test");
    @SuppressWarnings("unchecked")
    private HttpResponse<InputStream> response(int status, String body, Map<String,List<String>> headers) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (k,v) -> true));
        return response;
    }
    @Test void rejectsNonOfficialAndNonHttpsTargets() {
        for (String url : List.of("http://servicodados.ibge.gov.br/", "https://localhost/", "https://127.0.0.1/",
                "https://servicodados.ibge.gov.br.evil.test/", "https://u:p@servicodados.ibge.gov.br/", "https://servicodados.ibge.gov.br:9000/"))
            assertThrows(IllegalArgumentException.class, () -> client.download(URI.create(url)));
        verifyNoInteractions(transport);
    }
    @Test void rejectsRedirectOutsideOfficialHosts() throws Exception {
        var reply = response(302,"",Map.of("Location",List.of("https://localhost/secrets")));
        when(transport.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(reply);
        assertThrows(IllegalArgumentException.class, () -> client.download(official));
        verify(transport, times(1)).send(any(), any(HttpResponse.BodyHandler.class));
    }
    @Test void rejectsOversizedStreamEvenWithoutContentLength() throws Exception {
        limits.setMaxDownloadBytes(3);
        var reply = response(200,"1234",Map.of());
        when(transport.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(reply);
        assertThrows(ResponseStatusException.class, () -> client.download(official));
    }
    @Test void returnsNotFoundForUnpublishedCompetence() throws Exception {
        var reply = response(404,"missing",Map.of());
        when(transport.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(reply);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> client.download(official)).getStatusCode().value());
    }
    @Test void followsOfficialRelativeRedirectAndPreservesResponseBytes() throws Exception {
        var redirect = response(302,"",Map.of("Location",List.of("/api/data")));
        var success = response(200,"data",Map.of());
        when(transport.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(redirect,success);
        assertEquals("data",new String(client.download(official),java.nio.charset.StandardCharsets.UTF_8));
        verify(transport,times(2)).send(any(),any(HttpResponse.BodyHandler.class));
    }
}
