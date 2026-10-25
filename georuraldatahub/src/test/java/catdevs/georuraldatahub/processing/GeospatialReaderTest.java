package catdevs.georuraldatahub.processing;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;

class GeospatialReaderTest {
    private final GeospatialReader reader = new GeospatialReader(new ProcessingProperties());
    @Test void convertsActualShapefileAndGeoPackageAndReprojectsCoordinates() throws Exception {
        for (String name : List.of("parana-utm.gpkg", "parana-utm-shapefile.zip")) {
            try (var input = getClass().getResourceAsStream("/processing/" + name)) {
                var result = reader.read(input, name.endsWith("zip") ? "zip" : "gpkg", 0);
                assertEquals(31982, result.metadata().path("sourceSrid").asInt());
                var data = new ObjectMapper().readTree(result.content());
                assertEquals("4106902", data.path("features").get(0).path("properties").path("codarea").asText());
                var geometry = new org.locationtech.jts.io.geojson.GeoJsonReader().read(
                        data.path("features").get(0).path("geometry").toString());
                double lon = geometry.getCentroid().getX();
                assertTrue(lon < -48 && lon > -54, "A coordenada UTM precisa ter sido transformada para longitude.");
            }
        }
    }
    @Test void readsGeoTiffMetadataWithoutVectorizingCells() throws Exception {
        try (var input = getClass().getResourceAsStream("/processing/parana-utm.tif")) {
            var result = reader.read(input, "tif", 0);
            assertEquals("RASTER", result.metadata().path("kind").asText());
            var summary = new ObjectMapper().readTree(result.content());
            assertEquals(31982, summary.path("sourceSrid").asInt());
            assertEquals(8, summary.path("width").asInt()); assertEquals(6, summary.path("height").asInt());
            assertEquals(1, summary.path("bandCount").asInt()); assertEquals(30, summary.path("pixelWidth").asDouble());
            assertEquals("Byte", summary.path("dataTypes").asText());
            assertEquals("Polygon", summary.path("footprint").path("type").asText());
            assertEquals("255.0", summary.path("noDataValues").asText());
            assertEquals(129, summary.path("footprint").path("coordinates").get(0).size());
            assertEquals(6, result.metadata().path("geotransform").size());
            assertTrue(result.metadata().path("toolVersion").asText().startsWith("GeoTools "));
            assertFalse(result.metadata().has("features"));
        }
    }
    @Test void geographicRasterPreservesDegreeUnitsInsteadOfAssumingMeters() throws Exception {
        try (var input = getClass().getResourceAsStream("/processing/parana-geographic.tif")) {
            var result = reader.read(input, "tif", 0);
            var summary = new ObjectMapper().readTree(result.content());
            assertEquals(4326, summary.path("sourceSrid").asInt());
            assertTrue(summary.path("pixelUnit").asText().toLowerCase(Locale.ROOT).contains("degree"));
            assertTrue(summary.path("pixelWidth").asDouble() > 0 && summary.path("pixelWidth").asDouble() < 0.01);
        }
    }
    @Test void refusesInvalidTiffTraversalAndInputOverLimit() throws Exception {
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> reader.read(new ByteArrayInputStream("not-a-tiff".getBytes()), "tif", 0)).getStatusCode().value());
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("../outside.shp")); zip.write(1); zip.closeEntry();
        }
        var error = assertThrows(ResponseStatusException.class,
                () -> reader.read(new ByteArrayInputStream(bytes.toByteArray()), "zip", 0));
        assertTrue(error.getReason().contains("Caminho inválido"));
        var properties = new ProcessingProperties(); properties.setMaxInputBytes(2);
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> new GeospatialReader(properties).read(new ByteArrayInputStream(new byte[3]), "tif", 0)).getStatusCode().value());
    }

    @Test void explicitCrsReprojectionPreservesAttributesAndNormalizesCoordinatesToXY() throws Exception {
        String json = """
            {"type":"Feature","id":"original","crs":{"type":"name","properties":{"name":"EPSG:4326"}},
             "bbox":[-51,-25,-51,-25],"geometry":{"type":"Point","coordinates":[-51,-25,100]},
             "properties":{"nome":"ação","valor":3.5,"ativo":true,"vazio":null}}
            """;
        var result = reader.read(new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "geojson", 0);
        var original = new ObjectMapper().readTree(json);
        var feature = new ObjectMapper().readTree(result.content()).path("features").get(0);
        assertEquals(original.path("properties"), feature.path("properties"));
        assertEquals("original", feature.path("id").asText());
        assertEquals(2, feature.path("geometry").path("coordinates").size());
        assertEquals(-51, feature.path("geometry").path("coordinates").get(0).asDouble());
        assertFalse(feature.has("bbox")); assertFalse(feature.has("crs"));
    }

    @Test void doesNotGuessCrsForPlainTiff(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws Exception {
        var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
        var path = folder.resolve("sem-crs.tif");
        assertTrue(javax.imageio.ImageIO.write(image, "TIFF", path.toFile()));
        try (var input = java.nio.file.Files.newInputStream(path)) {
            assertEquals(422, assertThrows(ResponseStatusException.class, () -> reader.read(input, "tif", 0)).getStatusCode().value());
        }
    }

    @Test void rejectsMissingShapefileProjectionAndExpandedZipOverLimit() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var input = new ZipInputStream(getClass().getResourceAsStream("/processing/parana-utm-shapefile.zip"));
                var output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".prj")) continue;
                output.putNextEntry(new ZipEntry(entry.getName())); input.transferTo(output); output.closeEntry();
            }
        }
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> reader.read(new ByteArrayInputStream(bytes.toByteArray()), "zip", 0)).getStatusCode().value());
        var limits = new ProcessingProperties(); limits.setMaxOutputBytes(2);
        try (var input = getClass().getResourceAsStream("/processing/parana-utm-shapefile.zip")) {
            assertEquals(422, assertThrows(ResponseStatusException.class,
                    () -> new GeospatialReader(limits).read(input, "zip", 0)).getStatusCode().value());
        }
    }

    @Test void enforcesFeatureAndOutputLimitsWhileConvertingJson() throws Exception {
        String json = """
            {"type":"FeatureCollection","features":[
            {"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[-51,-25]}},
            {"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[-50,-24]}}]}
            """;
        var limits = new ProcessingProperties(); limits.setMaxFeatures(1);
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> new GeospatialReader(limits).read(new ByteArrayInputStream(bytes), "geojson", 0)).getStatusCode().value());
        limits.setMaxOutputBytes(2);
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> new GeospatialReader(limits).read(new ByteArrayInputStream(bytes), "geojson", 0)).getStatusCode().value());
    }

    @Test void geographicInputsUseLongitudeLatitudeEvenWithAuthorityAxisOrder(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws Exception {
        var factory = new org.geotools.geopkg.GeoPkgDataStoreFactory();
        var store = factory.createDataStore(Map.of("dbtype", "geopkg", "database", folder.resolve("latlon.gpkg").toFile()));
        try {
            var builder = new org.geotools.feature.simple.SimpleFeatureTypeBuilder();
            builder.setName("pontos"); builder.setCRS(org.geotools.referencing.CRS.decode("EPSG:4326", true));
            builder.add("geometry", org.locationtech.jts.geom.Point.class); builder.add("nome", String.class);
            var schema = builder.buildFeatureType(); store.createSchema(schema);
            var point = new org.locationtech.jts.geom.GeometryFactory().createPoint(new org.locationtech.jts.geom.Coordinate(-51, -25));
            var feature = org.geotools.feature.simple.SimpleFeatureBuilder.build(schema, new Object[]{point, "teste"}, null);
            ((org.geotools.api.data.SimpleFeatureStore) store.getFeatureSource("pontos"))
                    .addFeatures(new org.geotools.data.collection.ListFeatureCollection(schema, List.of(feature)));
        } finally { store.dispose(); }
        try (var input = java.nio.file.Files.newInputStream(folder.resolve("latlon.gpkg"))) {
            var result = reader.read(input, "gpkg", 0);
            var coordinates = new ObjectMapper().readTree(result.content()).path("features").get(0).path("geometry").path("coordinates");
            assertEquals(-51, coordinates.get(0).asDouble(), 0.000001);
            assertEquals(-25, coordinates.get(1).asDouble(), 0.000001);
        }
    }
}
