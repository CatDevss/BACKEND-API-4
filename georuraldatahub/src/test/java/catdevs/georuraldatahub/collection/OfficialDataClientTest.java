package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentMatchers;
import org.springframework.web.server.ResponseStatusException;

class OfficialDataClientTest {
    private final CollectionProperties limits = new CollectionProperties();
    private final HttpClient transport = mock(HttpClient.class);
    private final OfficialDataClient client = new OfficialDataClient(limits, transport);
    private final URI official = URI.create("https://servicodados.ibge.gov.br/api/test");
    @SuppressWarnings("unchecked")
    private HttpResponse<byte[]> response(int status, String body, Map<String,List<String>> headers) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (k,v) -> true));
        return response;
    }
    private void reply(HttpResponse<byte[]> response) {
        when(transport.sendAsync(any(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
    }
    @Test void rejectsNonOfficialAndNonHttpsTargets() {
        for (String url : List.of("http://servicodados.ibge.gov.br/", "https://localhost/", "https://127.0.0.1/",
                "https://servicodados.ibge.gov.br.evil.test/", "https://u:p@servicodados.ibge.gov.br/", "https://servicodados.ibge.gov.br:9000/"))
            assertThrows(IllegalArgumentException.class, () -> client.download(URI.create(url)));
        verifyNoInteractions(transport);
    }
    @Test void rejectsRedirectOutsideOfficialHosts() {
        reply(response(302,"",Map.of("Location",List.of("https://localhost/secrets"))));
        assertThrows(IllegalArgumentException.class, () -> client.download(official));
        verify(transport).sendAsync(any(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }
    @Test void rejectsOversizedBodyEvenWithoutContentLength() {
        limits.setMaxDownloadBytes(3); reply(response(200,"1234",Map.of()));
        assertThrows(ResponseStatusException.class, () -> client.download(official));
    }
    @Test void subscriberCancelsBeforeRetainingBytesBeyondLimit() {
        var subscriber = new OfficialDataClient.LimitedBodySubscriber(3);
        var subscription = mock(java.util.concurrent.Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2})));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{3,4})));
        verify(subscription).cancel();
        assertInstanceOf(ResponseStatusException.class, assertThrows(CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join()).getCause());
    }
    @Test void returnsNotFoundForUnpublishedCompetence() {
        reply(response(404,"missing",Map.of()));
        var error = assertThrows(ResponseStatusException.class, () -> client.download(official));
        assertEquals(404, error.getStatusCode().value()); assertTrue(error.getReason().contains("competência"));
    }
    @Test void followsOfficialRelativeRedirectAndPreservesResponseBytes() throws Exception {
        var redirect = CompletableFuture.completedFuture(response(302,"",Map.of("Location",List.of("/api/data"))));
        var success = CompletableFuture.completedFuture(response(200,"data",Map.of()));
        when(transport.sendAsync(any(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(redirect).thenReturn(success);
        assertEquals("data",new String(client.download(official),StandardCharsets.UTF_8));
        verify(transport,times(2)).sendAsync(any(),ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }
    @Test @Timeout(4) void stalledBodyHasDeadlineAndCancelsDownload() {
        limits.setRequestTimeoutSeconds(1);
        var pending = new CompletableFuture<HttpResponse<byte[]>>();
        when(transport.sendAsync(any(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(pending);
        assertEquals(504, assertThrows(ResponseStatusException.class, () -> client.download(official)).getStatusCode().value());
        assertTrue(pending.isCancelled());
    }
    @Test @Timeout(4) void totalCollectionBudgetAppliesEvenWhenPerDownloadTimeoutIsLonger() throws Exception {
        limits.setMaxDurationSeconds(1);
        limits.setRequestTimeoutSeconds(90);
        var pending = new CompletableFuture<HttpResponse<byte[]>>();
        when(transport.sendAsync(any(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(pending);
        try (var budget = client.collectionBudget()) {
            var error = assertThrows(ResponseStatusException.class, () -> client.download(official));
            assertEquals(504, error.getStatusCode().value());
            assertTrue(error.getReason().contains("prazo total"));
        }
        assertTrue(pending.isCancelled());
        reply(response(200, "restored", Map.of()));
        assertEquals("restored", new String(client.download(official), StandardCharsets.UTF_8));
    }
    @Test void htmlPageIsNotAcceptedAsADataFile() {
        reply(response(200,"<html>Portal</html>",Map.of("Content-Type",List.of("text/html"))));
        assertTrue(assertThrows(ResponseStatusException.class, () -> client.download(official)).getReason().contains("HTML"));
    }
    @Test void failedConnectionProducesAnExplicitReadFailure() {
        when(transport.sendAsync(any(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.failedFuture(new java.io.IOException("connection reset")));
        assertTrue(assertThrows(java.io.IOException.class, () -> client.download(official)).getMessage().contains("connection reset"));
    }
}
