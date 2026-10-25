package catdevs.georuraldatahub.processing;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.file.*;
import java.time.ZoneOffset;
import java.util.*;
import org.geotools.api.data.DataStore;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.*;

final class GeoToolsVectorReader {
    private final ProcessingProperties limits;
    private final ObjectMapper mapper = new ObjectMapper();

    GeoToolsVectorReader(ProcessingProperties limits) { this.limits = limits; }

    GeospatialReader.Result read(Path path, String format, int override, long deadline) throws Exception {
        if (format.equals("json") || format.equals("geojson")) return json(path, override, deadline);
        DataStore store;
        if (format.equals("zip")) {
            var shape = new ShapefileDataStore(path.toUri().toURL());
            shape.setTryCPGFile(true);
            shape.setTimeZone(TimeZone.getTimeZone("UTC"));
            shape.setIndexed(false);
            shape.setIndexCreationEnabled(false);
            store = shape;
        } else {
            var params = new HashMap<String, Object>();
            params.put("dbtype", "geopkg");
            params.put("database", path.toFile());
            params.put(GeoPkgDataStoreFactory.READ_ONLY.key, true);
            params.put(GeoPkgDataStoreFactory.CONTENTS_ONLY.key, true);
            store = new GeoPkgDataStoreFactory().createDataStore(params);
        }
        if (store == null) throw GeospatialReader.invalid("Não foi possível abrir a camada.");
        try {
            String[] names = store.getTypeNames();
            if (names.length != 1) throw GeospatialReader.invalid("Envie exatamente uma camada vetorial por arquivo.");
            var features = store.getFeatureSource(names[0]);
            var crs = override > 0 ? CRS.decode("EPSG:" + override, true) : features.getSchema().getCoordinateReferenceSystem();
            int srid = epsg(crs);
            var transform = transform(CRS.decode("EPSG:" + srid, true));
            var bytes = boundedOutput();
            try (var generator = mapper.getFactory().createGenerator(bytes);
                    var iterator = features.getFeatures().features()) {
                begin(generator);
                int count = 0;
                while (iterator.hasNext()) {
                    GeospatialReader.checkDeadline(deadline);
                    if (++count > limits.getMaxFeatures()) throw GeospatialReader.invalid("Camada excede o limite de feições.");
                    var original = iterator.next();
                    ObjectNode feature = mapper.createObjectNode().put("type", "Feature").put("id", original.getID());
                    feature.set("geometry", geometry((Geometry) original.getDefaultGeometry(), transform));
                    var properties = feature.putObject("properties");
                    for (var descriptor : original.getFeatureType().getAttributeDescriptors()) {
                        if (descriptor.equals(original.getFeatureType().getGeometryDescriptor())) continue;
                        Object value = original.getAttribute(descriptor.getName());
                        // Datas DBF não podem virar timestamps numéricos por configuração do serializador.
                        if (value instanceof java.sql.Date date) value = date.toLocalDate().toString();
                        else if (value instanceof java.util.Date date) value = format.equals("zip")
                                ? date.toInstant().atOffset(ZoneOffset.UTC).toLocalDate().toString() : date.toInstant().toString();
                        properties.set(descriptor.getLocalName(), mapper.valueToTree(value));
                    }
                    generator.writeTree(feature);
                }
                end(generator);
            }
            return new GeospatialReader.Result(bytes.toByteArray(), metadata(srid));
        } finally { store.dispose(); }
    }

    private GeospatialReader.Result json(Path path, int override, long deadline) throws Exception {
        if (Files.size(path) > limits.getMaxOutputBytes()) throw GeospatialReader.invalid("JSON excede o limite de leitura vetorial.");
        JsonNode root = mapper.readTree(path.toFile());
        if (root == null) throw GeospatialReader.invalid("GeoJSON vazio.");
        int srid = override;
        if (srid <= 0) {
            String declared = root.path("crs").path("properties").path("name").asText();
            if (declared.isBlank() && !root.hasNonNull("crs")) declared = "EPSG:4326";
            if (declared.isBlank()) throw GeospatialReader.invalid("CRS GeoJSON ausente ou inválido.");
            srid = epsg(CRS.decode(declared, true));
        }
        var transform = transform(CRS.decode("EPSG:" + srid, true));
        boolean single = "Feature".equals(root.path("type").asText());
        if (!single && !("FeatureCollection".equals(root.path("type").asText()) && root.path("features").isArray()))
            throw GeospatialReader.invalid("Envie um GeoJSON Feature ou FeatureCollection.");
        Iterable<JsonNode> features = single ? List.of(root) : root.path("features");
        var bytes = boundedOutput();
        try (var generator = mapper.getFactory().createGenerator(bytes)) {
            begin(generator);
            int count = 0;
            for (JsonNode original : features) {
                GeospatialReader.checkDeadline(deadline);
                if (++count > limits.getMaxFeatures()) throw GeospatialReader.invalid("Camada excede o limite de feições.");
                if (!original.isObject()) throw GeospatialReader.invalid("Feição GeoJSON inválida.");
                var feature = ((ObjectNode) original).deepCopy();
                JsonNode value = original.get("geometry");
                feature.set("geometry", geometry(value == null || value.isNull() ? null : new GeoJsonReader().read(value.toString()), transform));
                // Bbox/CRS anteriores não descrevem as coordenadas reprojetadas.
                feature.remove(List.of("bbox", "crs"));
                generator.writeTree(feature);
            }
            end(generator);
        }
        return new GeospatialReader.Result(bytes.toByteArray(), metadata(srid));
    }

    static int epsg(CoordinateReferenceSystem crs) throws Exception {
        if (crs == null) throw GeospatialReader.invalid("Sistema de coordenadas ausente; informe um CRS identificável por EPSG.");
        Integer srid = CRS.lookupEpsgCode(crs, true);
        if (srid == null) throw GeospatialReader.invalid("Sistema de coordenadas não identificável por EPSG.");
        return srid;
    }

    static MathTransform transform(CoordinateReferenceSystem crs) throws Exception {
        // Coordenadas dos arquivos são x/y; o GeoJSON de saída usa longitude/latitude.
        return CRS.findMathTransform(crs, CRS.decode("EPSG:4326", true), true);
    }

    private JsonNode geometry(Geometry original, MathTransform transform) throws Exception {
        if (original == null) return mapper.nullNode();
        Geometry geographic = JTS.transform(original, transform);
        for (var coordinate : geographic.getCoordinates())
            if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)
                    || Math.abs(coordinate.x) > 180 || Math.abs(coordinate.y) > 90)
                throw GeospatialReader.invalid("Geometria fora dos limites geográficos após reprojeção.");
        // Mesmo contrato 2D do Oracle/validador; Z/M permanecem preservados no arquivo bruto.
        geographic.apply(new org.locationtech.jts.geom.CoordinateSequenceFilter() {
            @Override public void filter(org.locationtech.jts.geom.CoordinateSequence sequence, int index) {
                if (sequence.hasZ()) sequence.setOrdinate(index, 2, Double.NaN);
            }
            @Override public boolean isDone() { return false; }
            @Override public boolean isGeometryChanged() { return true; }
        });
        var writer = new GeoJsonWriter(12);
        writer.setEncodeCRS(false);
        writer.setForceCCW(true);
        return mapper.readTree(writer.write(geographic));
    }

    private ObjectNode metadata(int srid) {
        return mapper.createObjectNode().put("kind", "VECTOR").put("sourceSrid", srid)
                .put("targetSrid", 4326).put("toolVersion", "GeoTools " + GeoTools.getVersion());
    }

    private ByteArrayOutputStream boundedOutput() {
        return new ByteArrayOutputStream() {
            @Override public synchronized void write(byte[] bytes, int offset, int length) {
                if ((long) size() + length > limits.getMaxOutputBytes()) throw GeospatialReader.invalid("GeoJSON convertido excede o limite de tamanho.");
                super.write(bytes, offset, length);
            }
            @Override public synchronized void write(int value) {
                if (size() >= limits.getMaxOutputBytes()) throw GeospatialReader.invalid("GeoJSON convertido excede o limite de tamanho.");
                super.write(value);
            }
        };
    }

    private void begin(JsonGenerator generator) throws IOException {
        generator.writeStartObject(); generator.writeStringField("type", "FeatureCollection");
        generator.writeArrayFieldStart("features");
    }
    private void end(JsonGenerator generator) throws IOException { generator.writeEndArray(); generator.writeEndObject(); }
}
