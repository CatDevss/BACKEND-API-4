package catdevs.georuraldatahub.validation;

import com.fasterxml.jackson.databind.JsonNode;

final class GeometryValidation {
    private GeometryValidation() {}
    static boolean valid(JsonNode geometry) {
        if (geometry == null || !geometry.isObject()) return false;
        JsonNode c = geometry.path("coordinates");
        boolean structure = switch (geometry.path("type").asText()) {
            case "Point" -> position(c);
            case "MultiPoint" -> positions(c, 1);
            case "LineString" -> positions(c, 2);
            case "MultiLineString" -> children(c, 1, false);
            case "Polygon" -> polygon(c);
            case "MultiPolygon" -> children(c, 2, false);
            case "GeometryCollection" -> {
                JsonNode geometries = geometry.path("geometries");
                boolean ok = geometries.isArray() && !geometries.isEmpty();
                for (JsonNode child : geometries) ok &= valid(child);
                yield ok;
            }
            default -> false;
        };
        if (!structure) return false;
        try { return topology(geometry).isValid(); }
        catch (IllegalArgumentException e) { return false; }
    }
    private static final org.locationtech.jts.geom.GeometryFactory FACTORY = new org.locationtech.jts.geom.GeometryFactory();
    private static org.locationtech.jts.geom.Coordinate[] coordinates(JsonNode nodes) {
        var result = new org.locationtech.jts.geom.Coordinate[nodes.size()];
        for (int i = 0; i < result.length; i++) result[i] = new org.locationtech.jts.geom.Coordinate(
                nodes.get(i).get(0).asDouble(), nodes.get(i).get(1).asDouble());
        return result;
    }
    private static org.locationtech.jts.geom.Polygon polygonGeometry(JsonNode rings) {
        var shell = FACTORY.createLinearRing(coordinates(rings.get(0)));
        var holes = new org.locationtech.jts.geom.LinearRing[rings.size() - 1];
        for (int i = 0; i < holes.length; i++) holes[i] = FACTORY.createLinearRing(coordinates(rings.get(i + 1)));
        return FACTORY.createPolygon(shell, holes);
    }
    private static org.locationtech.jts.geom.Geometry topology(JsonNode g) {
        JsonNode c = g.path("coordinates");
        return switch (g.path("type").asText()) {
            case "Point" -> FACTORY.createPoint(new org.locationtech.jts.geom.Coordinate(c.get(0).asDouble(), c.get(1).asDouble()));
            case "MultiPoint" -> FACTORY.createMultiPointFromCoords(coordinates(c));
            case "LineString" -> FACTORY.createLineString(coordinates(c));
            case "Polygon" -> polygonGeometry(c);
            case "MultiLineString" -> {
                var lines = new org.locationtech.jts.geom.LineString[c.size()];
                for (int i = 0; i < lines.length; i++) lines[i] = FACTORY.createLineString(coordinates(c.get(i)));
                yield FACTORY.createMultiLineString(lines);
            }
            case "MultiPolygon" -> {
                var polygons = new org.locationtech.jts.geom.Polygon[c.size()];
                for (int i = 0; i < polygons.length; i++) polygons[i] = polygonGeometry(c.get(i));
                yield FACTORY.createMultiPolygon(polygons);
            }
            case "GeometryCollection" -> {
                var geometries = new org.locationtech.jts.geom.Geometry[g.path("geometries").size()];
                for (int i = 0; i < geometries.length; i++) geometries[i] = topology(g.path("geometries").get(i));
                yield FACTORY.createGeometryCollection(geometries);
            }
            default -> throw new IllegalArgumentException("Geometria inválida.");
        };
    }
    private static boolean position(JsonNode p) {
        if (!p.isArray() || p.size() < 2) return false;
        for (JsonNode n : p) if (!n.isNumber() || !Double.isFinite(n.asDouble())) return false;
        return Math.abs(p.get(0).asDouble()) <= 180 && Math.abs(p.get(1).asDouble()) <= 90;
    }
    private static boolean positions(JsonNode c, int minimum) {
        if (!c.isArray() || c.size() < minimum) return false;
        int dimension = c.get(0).size();
        for (JsonNode p : c) if (!position(p) || p.size() != dimension) return false;
        return true;
    }
    private static boolean polygon(JsonNode c) { return children(c, 0, true); }
    private static boolean children(JsonNode c, int level, boolean rings) {
        if (!c.isArray() || c.isEmpty()) return false;
        for (JsonNode part : c) {
            if (rings) {
                if (!positions(part, 4) || !part.get(0).equals(part.get(part.size() - 1))) return false;
            } else if (!(level == 1 ? positions(part, 2) : polygon(part))) return false;
        }
        return true;
    }
}
