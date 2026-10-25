package catdevs.georuraldatahub.collection;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

/** Reads the polygon/DBF subset used by official IBGE biome and municipal archives.
 * Unsupported projections/types fail explicitly. Nothing is extracted to disk. */
@Component
public class BiomesShapefileReader {
    private final CollectionProperties limits;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeometryFactory factory = new GeometryFactory();
    public BiomesShapefileReader(CollectionProperties limits) { this.limits = limits; }

    public byte[] toGeoJson(byte[] archive) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        long expanded = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry; int entries = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 100) throw invalid("ZIP com componentes demais.");
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.contains("..") || name.startsWith("/") || name.contains("\\")) throw invalid("Caminho ZIP inválido.");
                int remaining = (int) Math.max(0, limits.getMaxTotalBytes() - expanded);
                byte[] content = zip.readNBytes(remaining + 1);
                expanded += content.length;
                if (expanded > limits.getMaxTotalBytes()) throw invalid("ZIP excede o limite descompactado.");
                if (files.put(name, content) != null) throw invalid("Componente ZIP duplicado.");
            }
        }
        List<String> shapes = files.keySet().stream().filter(n -> n.endsWith(".shp")).toList();
        if (shapes.size() != 1) throw invalid("O ZIP IBGE deve conter exatamente um shapefile.");
        String base = shapes.getFirst().replaceFirst("\\.shp$", "");
        byte[] shape = files.get(base + ".shp"), dbf = files.get(base + ".dbf");
        byte[] projection = files.getOrDefault(base + ".prj", files.get(base + ".prj.txt"));
        if (dbf == null || projection == null) throw invalid("DBF ou PRJ ausente.");
        String prj = new String(projection, StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
        if (!prj.startsWith("GEOGCS[") || !prj.contains("SIRGAS_2000") || !prj.contains("DEGREE"))
            throw invalid("Apenas a base geográfica SIRGAS 2000 é aceita; reprojeção não será presumida.");
        String encoding = new String(files.getOrDefault(base + ".cpg", "ISO-8859-1".getBytes(StandardCharsets.US_ASCII)), StandardCharsets.US_ASCII).trim();
        if (encoding.equals("1252")) encoding = "windows-1252";
        if (encoding.equals("65001")) encoding = "UTF-8";
        List<ObjectNode> attributes = attributes(dbf, Charset.forName(encoding));
        if (shape.length < 100 || ByteBuffer.wrap(shape).getInt() != 9994) throw invalid("Cabeçalho SHP inválido.");
        ByteBuffer input = ByteBuffer.wrap(shape).order(ByteOrder.LITTLE_ENDIAN);
        if (input.getInt(28) != 1000 || input.getInt(32) != 5) throw invalid("Apenas shapefile Polygon é suportado.");
        long declared = Integer.toUnsignedLong(ByteBuffer.wrap(shape).getInt(24)) * 2;
        if (declared != shape.length) throw invalid("Shapefile truncado.");
        ObjectNode root = mapper.createObjectNode().put("type", "FeatureCollection");
        ArrayNode features = root.putArray("features");
        int offset = 100, record = 0;
        while (offset < shape.length) {
            if (offset + 8 > shape.length || record >= attributes.size()) throw invalid("Registros SHP/DBF incompatíveis.");
            int size = ByteBuffer.wrap(shape, offset + 4, 4).getInt() * 2;
            if (size < 4 || (long) offset + 8 + size > shape.length) throw invalid("Registro SHP truncado.");
            ByteBuffer data = ByteBuffer.wrap(shape, offset + 8, size).slice().order(ByteOrder.LITTLE_ENDIAN);
            ObjectNode properties = attributes.get(record++);
            if (properties == null) throw invalid("Base IBGE contém registro excluído no DBF.");
            ObjectNode feature = features.addObject().put("type", "Feature");
            feature.set("properties", properties);
            feature.set("geometry", geometry(data));
            offset += 8 + size;
        }
        if (record != attributes.size() || record == 0) throw invalid("Quantidade de registros SHP/DBF incompatível.");
        return mapper.writeValueAsBytes(root);
    }
    private ObjectNode geometry(ByteBuffer data) throws IOException {
        if (data.remaining() < 44 || data.getInt() != 5) throw invalid("Registro não é Polygon.");
        data.position(36);
        int parts = data.getInt(), points = data.getInt();
        if (parts < 1 || points < 4 || parts > points || 44L + parts * 4L + points * 16L != data.limit())
            throw invalid("Contagem de partes/pontos SHP inválida.");
        int[] starts = new int[parts + 1];
        for (int i = 0; i < parts; i++) starts[i] = data.getInt();
        starts[parts] = points;
        if (starts[0] != 0) throw invalid("Índice inicial de anel inválido.");
        List<LinearRing> shells = new ArrayList<>(), holes = new ArrayList<>();
        for (int i = 0; i < parts; i++) {
            int count = starts[i + 1] - starts[i];
            if (count < 4) throw invalid("Anel SHP incompleto.");
            Coordinate[] coordinates = new Coordinate[count];
            for (int j = 0; j < count; j++) {
                double x = data.getDouble(), y = data.getDouble();
                if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 180 || Math.abs(y) > 90)
                    throw invalid("Coordenadas fora da referência geográfica.");
                coordinates[j] = new Coordinate(x, y);
            }
            if (!coordinates[0].equals2D(coordinates[count - 1])) throw invalid("Anel SHP não fechado.");
            LinearRing ring = factory.createLinearRing(coordinates);
            (Orientation.isCCW(coordinates) ? holes : shells).add(ring);
        }
        if (shells.isEmpty()) throw invalid("Polígono SHP sem contorno exterior.");
        List<List<LinearRing>> assigned = new ArrayList<>();
        List<Polygon> polygons = shells.stream().map(factory::createPolygon).toList();
        for (int i = 0; i < shells.size(); i++) assigned.add(new ArrayList<>());
        var prepared = polygons.stream().map(org.locationtech.jts.geom.prep.PreparedGeometryFactory::prepare).toList();
        double[] areas = polygons.stream().mapToDouble(Polygon::getArea).toArray();
        for (LinearRing hole : holes) {
            int owner = -1; double smallest = Double.POSITIVE_INFINITY;
            Point point = factory.createPoint(hole.getCoordinateN(0));
            for (int i = 0; i < polygons.size(); i++) {
                if (areas[i] < smallest && prepared.get(i).covers(point)) { owner = i; smallest = areas[i]; }
            }
            if (owner < 0) throw invalid("Buraco sem polígono exterior.");
            assigned.get(owner).add(hole);
        }
        ObjectNode geometry = mapper.createObjectNode().put("type", "MultiPolygon");
        ArrayNode coordinates = geometry.putArray("coordinates");
        for (int i = 0; i < shells.size(); i++) {
            ArrayNode polygon = coordinates.addArray();
            ring(polygon, shells.get(i));
            for (LinearRing hole : assigned.get(i)) ring(polygon, hole);
        }
        return geometry;
    }
    private void ring(ArrayNode polygon, LinearRing ring) {
        ArrayNode points = polygon.addArray();
        for (Coordinate c : ring.getCoordinates()) points.addArray().add(c.x).add(c.y);
    }
    private List<ObjectNode> attributes(byte[] bytes, Charset encoding) throws IOException {
        if (bytes.length < 33) throw invalid("DBF truncado.");
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int records = input.getInt(4), header = Short.toUnsignedInt(input.getShort(8)), size = Short.toUnsignedInt(input.getShort(10));
        if (records < 1 || records > limits.getMaxFeatures() || header < 33 || size < 1
                || header > bytes.length || header + (long) size * records > bytes.length || (header - 33) % 32 != 0)
            throw invalid("Tamanho DBF inválido.");
        record Field(String name, char type, int length) {}
        List<Field> fields = new ArrayList<>();
        int expected = 1;
        for (int pos = 32; pos < header - 1; pos += 32) {
            String name = new String(bytes, pos, 11, StandardCharsets.US_ASCII).split("\\u0000", 2)[0];
            int length = Byte.toUnsignedInt(bytes[pos + 16]);
            fields.add(new Field(name, (char) bytes[pos + 11], length)); expected += length;
        }
        if (expected != size) throw invalid("Campos DBF não correspondem ao registro.");
        List<ObjectNode> result = new ArrayList<>();
        for (int row = 0; row < records; row++) {
            int offset = header + row * size;
            if (bytes[offset++] == '*') { result.add(null); continue; }
            ObjectNode properties = mapper.createObjectNode();
            for (Field field : fields) {
                String value = new String(bytes, offset, field.length(), encoding).trim(); offset += field.length();
                if (value.isBlank()) properties.putNull(field.name());
                else if (field.type() == 'N' || field.type() == 'F') properties.put(field.name(), new java.math.BigDecimal(value));
                else properties.put(field.name(), value);
            }
            result.add(properties);
        }
        return result;
    }
    private IOException invalid(String reason) { return new IOException("Base IBGE inválida: " + reason); }
}
