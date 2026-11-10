package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;

class RemoteFeatureReaderTest {
    private final OfficialDataClient http = mock(OfficialDataClient.class);
    private final CollectionProperties limits = new CollectionProperties();
    private final RemoteFeatureReader reader = new RemoteFeatureReader(http, limits);
    private byte[] page(int total, String id) {
        return ("{\"type\":\"FeatureCollection\",\"numberMatched\":" + total + ",\"features\":["
                + (id == null ? "" : "{\"type\":\"Feature\",\"id\":\"" + id + "\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[-50,-25]}}")
                + "]}").getBytes(StandardCharsets.UTF_8);
    }
    private RemoteFeatureReader.Result collect() throws Exception {
        return reader.wfs("https://geoservicos.inde.gov.br/geoserver/ICMBio/ows", "ICMBio:limiteucsfederais_a", null, "ogc_fid", null, null, false);
    }
    @Test void readsShortServerPagesUntilVerifiedTotalRatherThanTruncating() throws Exception {
        when(http.download(any())).thenReturn(page(2,"one"), page(2,"two"));
        var result = collect();
        assertEquals(2, new ObjectMapper().readTree(result.content()).path("features").size());
        assertEquals(2, result.originals().size());
        verify(http).download(argThat(u -> u.getQuery().contains("startIndex=1")));
    }
    @Test void rejectsRepeatedPagesAndChangingCounts() throws Exception {
        when(http.download(any())).thenReturn(page(2,"one"));
        assertThrows(ResponseStatusException.class, this::collect);
        when(http.download(any())).thenReturn(page(2,"one"), page(3,"two"));
        assertThrows(ResponseStatusException.class, this::collect);
    }
    @Test void rejectsEarlyEndAndMissingTotal() throws Exception {
        when(http.download(any())).thenReturn(page(2,"one"), page(2,null));
        assertThrows(ResponseStatusException.class, this::collect);
        when(http.download(any())).thenReturn(page(-1,"one"));
        assertThrows(ResponseStatusException.class, this::collect);
    }
    @Test void rejectsExcessBeforeDownloadingMorePagesAndAcceptsZero() throws Exception {
        limits.setMaxFeatures(1);
        when(http.download(any())).thenReturn(page(2,"one"));
        assertThrows(ResponseStatusException.class, this::collect);
        verify(http).download(any());
        when(http.download(any())).thenReturn(page(0,null));
        assertEquals(0,new ObjectMapper().readTree(collect().content()).path("features").size());
    }
}
