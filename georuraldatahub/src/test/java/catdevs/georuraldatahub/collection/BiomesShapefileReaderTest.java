package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class BiomesShapefileReaderTest {
    private final CollectionProperties limits = new CollectionProperties();
    private final BiomesShapefileReader reader = new BiomesShapefileReader(limits);
    @Test void readsRealArchiveAndPreservesAttributesAndPolygonCoordinates() throws Exception {
        var json = new ObjectMapper().readTree(reader.toGeoJson(PublicDataConnectorsTest.fixture("ibge-biomas-2004.zip")));
        assertEquals(10, json.path("features").size());
        for (var feature : json.path("features")) {
            assertFalse(feature.path("properties").isEmpty());
            assertEquals("MultiPolygon", feature.path("geometry").path("type").asText());
            assertFalse(feature.path("geometry").path("coordinates").isEmpty());
        }
    }
    @Test void rejectsExpansionAboveLimit() throws Exception {
        limits.setMaxTotalBytes(100);
        assertThrows(IOException.class, () -> reader.toGeoJson(PublicDataConnectorsTest.fixture("ibge-biomas-2004.zip")));
    }
    @Test void acceptsLegacyZipNamesWithoutUtf8Flag() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var source = new ZipInputStream(new ByteArrayInputStream(PublicDataConnectorsTest.fixture("ibge-biomas-2004.zip")));
                var zip = new ZipOutputStream(bytes, java.nio.charset.Charset.forName("windows-1252"))) {
            zip.putNextEntry(new ZipEntry("Informações técnicas.txt"));
            zip.write(new byte[]{1}); zip.closeEntry();
            ZipEntry entry;
            while ((entry = source.getNextEntry()) != null) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                source.transferTo(zip); zip.closeEntry();
            }
        }
        assertEquals(10, new ObjectMapper().readTree(reader.toGeoJson(bytes.toByteArray())).path("features").size());
    }
    @Test void rejectsZipTraversalAndMalformedArchives() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("../evil.shp")); zip.write(new byte[]{1}); zip.closeEntry();
        }
        assertThrows(IOException.class, () -> reader.toGeoJson(bytes.toByteArray()));
        assertThrows(IOException.class, () -> reader.toGeoJson(new byte[]{1,2,3}));
    }
}
