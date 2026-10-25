package catdevs.georuraldatahub.collection;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.io.geojson.*;
import org.locationtech.jts.operation.overlayng.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

final class ParanaSpatialFilter {
    static final String BOUNDARY_URL = "https://servicodados.ibge.gov.br/api/v3/malhas/estados/PR?formato=application%2Fvnd.geo%2Bjson&qualidade=maxima&periodo=2022";
    static final String LIMITATION = "Escopo exclusivo do Paraná (UF 41). Recorte espacial usa a malha simplificada IBGE 2022, qualidade máxima; não substitui limite cadastral. Geometrias inválidas podem ser reparadas para a interseção; os originais são preservados. Sem cálculo de área ou reprojeção equivalente nesta etapa.";
    private final OfficialDataClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeoJsonReader reader = new GeoJsonReader();
    private final GeoJsonWriter writer = new GeoJsonWriter(15);
    ParanaSpatialFilter(OfficialDataClient http) { this.http = http; writer.setEncodeCRS(false); }
    record Boundary(Geometry geometry, CollectedData.Original original) {}
    Boundary boundary() throws IOException, InterruptedException {
        byte[] data = http.download(URI.create(BOUNDARY_URL));
        JsonNode root = mapper.readTree(data);
        if (root == null || !root.path("type").asText().equals("FeatureCollection") || root.path("features").size() != 1
                || !root.path("features").get(0).path("properties").path("codarea").asText().equals("41"))
            throw OfficialDataClient.upstream("IBGE não retornou o limite oficial do Paraná (UF 41).");
        Geometry geometry = parse(root.path("features").get(0).path("geometry"));
        geometry = GeometryFixer.fix(geometry);
        if (geometry.isEmpty() || geometry.getDimension() != 2) throw OfficialDataClient.upstream("Limite do Paraná inválido.");
        return new Boundary(geometry, new CollectedData.Original("limite_PR_IBGE_2022.geojson",BOUNDARY_URL,"application/geo+json",data));
    }
    // Recorta nas coordenadas geográficas recebidas; não reprojeta nem calcula área.
    byte[] clip(byte[] input, Boundary boundary) throws IOException {
        JsonNode root = mapper.readTree(input);
        if (root == null || !root.path("type").asText().equals("FeatureCollection") || !root.path("features").isArray())
            throw OfficialDataClient.upstream("O recorte do Paraná exige uma FeatureCollection.");
        ObjectNode output = mapper.createObjectNode().put("type","FeatureCollection");
        ArrayNode features = output.putArray("features");
        for (JsonNode feature : root.path("features")) {
            http.checkCollectionDeadline();
            Geometry source = parse(feature.path("geometry"));
            if (source.isEmpty() || !source.getEnvelopeInternal().intersects(boundary.geometry().getEnvelopeInternal())) continue;
            Geometry intersection = OverlayNGRobust.overlay(GeometryFixer.fix(source), boundary.geometry(), OverlayNG.INTERSECTION);
            List<Polygon> polygons = new java.util.ArrayList<>();
            // Contatos apenas por linha ou ponto não representam área de bioma ou desmatamento.
            polygons(intersection, polygons);
            if (polygons.isEmpty()) continue;
            Geometry result = new GeometryFactory().createMultiPolygon(polygons.toArray(Polygon[]::new));
            ObjectNode copy = ((ObjectNode) feature).deepCopy();
            copy.remove(List.of("bbox","geometry_name"));
            copy.set("geometry",mapper.readTree(writer.write(result)));
            features.add(copy);
        }
        return mapper.writeValueAsBytes(output);
    }
    private void polygons(Geometry geometry,List<Polygon> result) {
        if (geometry instanceof Polygon p) result.add(p);
        else if (geometry instanceof GeometryCollection collection)
            for(int i=0;i<collection.getNumGeometries();i++) polygons(collection.getGeometryN(i),result);
    }
    private Geometry parse(JsonNode geometry) throws IOException {
        try { return reader.read(geometry.toString()); }
        catch (org.locationtech.jts.io.ParseException | IllegalArgumentException e) { throw new IOException("Geometria inválida para recorte do Paraná.",e); }
    }
}
