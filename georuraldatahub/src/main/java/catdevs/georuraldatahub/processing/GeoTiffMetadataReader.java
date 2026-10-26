package catdevs.georuraldatahub.processing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.awt.geom.AffineTransform;
import java.nio.file.Path;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.geotools.api.referencing.crs.ProjectedCRS;
import org.geotools.api.referencing.datum.PixelInCell;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.util.factory.GeoTools;
import org.geotools.util.factory.Hints;
import org.w3c.dom.*;

/** Consulta a grade e as tags TIFF; nunca chama read() para carregar a imagem/células. */
final class GeoTiffMetadataReader {
    private final ObjectMapper mapper = new ObjectMapper();

    GeospatialReader.Result read(Path path, long deadline) throws Exception {
        var reader = new GeoTiffReader(path.toFile(), new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, true));
        try {
            var metadata = reader.getMetadata();
            if (!metadata.hasGeoKey() || !(metadata.hasModelTrasformation() || metadata.hasPixelScales() && metadata.hasTiePoints()))
                throw GeospatialReader.invalid("GeoTIFF sem sistema de coordenadas ou georreferenciamento afim.");
            var crs = reader.getCoordinateReferenceSystem();
            int srid = GeoToolsVectorReader.epsg(crs);
            var grid = reader.getOriginalGridRange();
            int width = grid.getSpan(0), height = grid.getSpan(1);
            var gridToWorld = reader.getOriginalGridToWorld(PixelInCell.CELL_CORNER);
            if (!(gridToWorld instanceof AffineTransform affine) || width < 1 || height < 1
                    || Math.abs(affine.getDeterminant()) < 1e-20)
                throw GeospatialReader.invalid("Grade raster inválida ou não afim.");
            var geographicTransform = GeoToolsVectorReader.transform(crs);
            List<double[]> original = new ArrayList<>(), geographic = new ArrayList<>();
            int[][] corners = {{0, 0}, {width, 0}, {width, height}, {0, height}, {0, 0}};
            for (int edge = 0; edge < 4; edge++) {
                for (int step = 0; step < 32; step++) {
                    GeospatialReader.checkDeadline(deadline);
                    double ratio = step / 32.0;
                    double[] point = {grid.getLow(0) + corners[edge][0] + (corners[edge + 1][0] - corners[edge][0]) * ratio,
                            grid.getLow(1) + corners[edge][1] + (corners[edge + 1][1] - corners[edge][1]) * ratio};
                    double[] world = new double[2], lonLat = new double[2];
                    gridToWorld.transform(point, 0, world, 0, 1);
                    geographicTransform.transform(world, 0, lonLat, 0, 1);
                    if (!Double.isFinite(world[0]) || !Double.isFinite(world[1]) || !Double.isFinite(lonLat[0])
                            || !Double.isFinite(lonLat[1]) || Math.abs(lonLat[0]) > 180 || Math.abs(lonLat[1]) > 90)
                        throw GeospatialReader.invalid("Área coberta fora das coordenadas geográficas válidas.");
                    original.add(world); geographic.add(lonLat);
                }
            }
            original.add(original.getFirst()); geographic.add(geographic.getFirst());
            double orientation = 0;
            for (int i = 1; i < geographic.size(); i++) {
                double[] a = geographic.get(i - 1), b = geographic.get(i);
                orientation += a[0] * b[1] - b[0] * a[1];
            }
            if (orientation < 0) { Collections.reverse(original); Collections.reverse(geographic); }

            Map<Integer, List<String>> tags = new HashMap<>();
            collectTags(metadata.getRootNode(), tags);
            int bandCount = integer(tags, 277, 0, 1);
            if (bandCount < 1 || bandCount > 65535) throw GeospatialReader.invalid("Número de bandas inválido.");
            String noData = metadata.hasNoData() ? Double.toString(metadata.getNoData()) : null;
            Map<Integer, String> units = bandUnits(metadata.getAsciiTIFFTag("42112"));
            var bands = mapper.createArrayNode();
            List<String> types = new ArrayList<>(), noDataValues = new ArrayList<>();
            for (int band = 0; band < bandCount; band++) {
                GeospatialReader.checkDeadline(deadline);
                String type = dataType(integer(tags, 258, band, 1), integer(tags, 339, band, 1));
                types.add(type); noDataValues.add(noData == null ? "" : noData);
                bands.addObject().put("number", band + 1).put("dataType", type).put("noData", noData).put("unit", units.get(band));
                if ((long) bandCount * 100 > ProcessingProperties.MAX_METADATA_BYTES)
                    throw GeospatialReader.invalid("Metadados das bandas excedem o limite.");
            }
            String unit = crs.getCoordinateSystem().getAxis(0).getUnit().toString();
            // Mantém os nomes já utilizados pelo contrato do dicionário para os casos usuais.
            if (unit.equals("m")) unit = "metre";
            else if (!(crs instanceof ProjectedCRS) && (unit.equals("°") || unit.equals("deg"))) unit = "degree";
            var summary = mapper.createObjectNode().put("sourceSrid", srid).put("width", width).put("height", height)
                    .put("pixelWidth", Math.hypot(affine.getScaleX(), affine.getShearY()))
                    .put("pixelHeight", Math.hypot(affine.getShearX(), affine.getScaleY())).put("pixelUnit", unit)
                    .put("bandCount", bandCount).put("dataTypes", String.join(",", types))
                    .put("noDataValues", String.join(",", noDataValues));
            summary.set("footprint", polygon(geographic));
            double[] origin = new double[2];
            gridToWorld.transform(new double[]{grid.getLow(0), grid.getLow(1)}, 0, origin, 0, 1);
            var result = mapper.createObjectNode().put("kind", "RASTER").put("sourceSrid", srid)
                    .put("toolVersion", "GeoTools " + GeoTools.getVersion());
            result.putArray("geotransform").add(origin[0]).add(affine.getScaleX()).add(affine.getShearX())
                    .add(origin[1]).add(affine.getShearY()).add(affine.getScaleY());
            result.set("bands", bands); result.set("originalFootprint", polygon(original)); result.set("summary", summary);
            return new GeospatialReader.Result(mapper.writeValueAsBytes(summary), result);
        } finally { reader.dispose(); }
    }

    private ObjectNode polygon(List<double[]> points) {
        var polygon = mapper.createObjectNode().put("type", "Polygon");
        var ring = polygon.putArray("coordinates").addArray();
        for (double[] point : points) ring.addArray().add(point[0]).add(point[1]);
        return polygon;
    }

    private void collectTags(Node node, Map<Integer, List<String>> tags) {
        if ("TIFFField".equals(node.getNodeName())) {
            String number = node.getAttributes().getNamedItem("number").getNodeValue();
            List<String> values = new ArrayList<>();
            collectValues(node, values);
            tags.put(Integer.valueOf(number), values);
            return;
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) collectTags(child, tags);
    }
    private void collectValues(Node node, List<String> values) {
        Node value = node.getAttributes() == null ? null : node.getAttributes().getNamedItem("value");
        if (value != null) values.add(value.getNodeValue());
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) collectValues(child, values);
    }
    private int integer(Map<Integer, List<String>> tags, int tag, int band, int fallback) {
        var values = tags.get(tag);
        return values == null || values.isEmpty() ? fallback : Integer.parseInt(values.get(Math.min(band, values.size() - 1)));
    }
    private String dataType(int bits, int format) {
        if (format == 1) return bits <= 8 ? "Byte" : "UInt" + bits;
        if (format == 2) return "Int" + bits;
        if (format == 3) return "Float" + bits;
        if (format == 5) return "CInt" + bits / 2;
        if (format == 6) return "CFloat" + bits / 2;
        return "Unknown";
    }
    private Map<Integer, String> bandUnits(String xml) throws Exception {
        if (xml == null || xml.isBlank()) return Map.of();
        if (xml.length() > ProcessingProperties.MAX_METADATA_BYTES) throw GeospatialReader.invalid("Metadados TIFF excedem o limite.");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        Map<Integer, String> units = new HashMap<>();
        var items = document.getElementsByTagName("Item");
        for (int i = 0; i < items.getLength(); i++) {
            var item = (Element) items.item(i);
            if (item.getAttribute("role").equalsIgnoreCase("unittype") || item.getAttribute("name").equalsIgnoreCase("UNITTYPE"))
                units.put(Integer.parseInt(item.getAttribute("sample")), item.getTextContent());
        }
        return units;
    }
}
