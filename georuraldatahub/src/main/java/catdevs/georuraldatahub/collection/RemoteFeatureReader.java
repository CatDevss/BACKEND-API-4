package catdevs.georuraldatahub.collection;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.locationtech.jts.geom.Envelope;
import static catdevs.georuraldatahub.collection.PublishedResources.encode;

/** Paginação completa com total verificável, ordenação e detecção de respostas repetidas. */
final class RemoteFeatureReader {
    private final OfficialDataClient http;
    private final CollectionProperties limits;
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    RemoteFeatureReader(OfficialDataClient http, CollectionProperties limits) { this.http = http; this.limits = limits; }
    record Result(byte[] content, List<CollectedData.Original> originals) {}

    Result wfs(String endpoint, String layer, String filter, String sort, String fields, Envelope bbox, boolean gml)
            throws IOException, InterruptedException {
        String base = endpoint + (endpoint.contains("?") ? "&" : "?") + "service=WFS&version=2.0.0&request=GetFeature&typeNames=" + encode(layer)
                + "&srsName=" + encode(gml ? "urn:ogc:def:crs:EPSG::4326" : "CRS:84")
                + (gml ? "" : "&outputFormat=application%2Fjson") + "&sortBy=" + encode(sort);
        if (filter != null) base += "&CQL_FILTER=" + encode(filter);
        if (fields != null) base += "&propertyName=" + encode(fields);
        if (bbox != null) base += "&bbox=" + encode(gml
                ? bbox.getMinY() + "," + bbox.getMinX() + "," + bbox.getMaxY() + "," + bbox.getMaxX() + ",urn:ogc:def:crs:EPSG::4326"
                : bbox.getMinX() + "," + bbox.getMinY() + "," + bbox.getMaxX() + "," + bbox.getMaxY() + ",CRS:84");
        String query = base;
        long total = gml ? parse(http.download(URI.create(base + "&resultType=hits")), true).path("numberMatched").asLong(-1) : -1;
        return pages(start -> query + "&count=" + limits.getPageSize() + "&startIndex=" + start, gml, total);
    }

    Result arcgis(String layer, String fields, Envelope bbox) throws IOException, InterruptedException {
        String query = layer + "/query?where=1%3D1&geometryType=esriGeometryEnvelope&inSR=4326&spatialRel=esriSpatialRelIntersects"
                + "&geometry=" + encode(bbox.getMinX() + "," + bbox.getMinY() + "," + bbox.getMaxX() + "," + bbox.getMaxY());
        JsonNode count = parse(http.download(URI.create(query + "&returnCountOnly=true&f=json")), false);
        long total = count.path("count").asLong(-1);
        return pages(start -> query + "&outFields=" + encode(fields) + "&outSR=4326&f=geojson&orderByFields=objectid"
                + "&resultRecordCount=" + limits.getPageSize() + "&resultOffset=" + start, false, total);
    }

    private Result pages(java.util.function.IntFunction<String> url, boolean gml, long expected)
            throws IOException, InterruptedException {
        var output = mapper.createObjectNode().put("type", "FeatureCollection");
        var features = output.putArray("features");
        var originals = new ArrayList<CollectedData.Original>();
        var ids = new HashSet<String>();
        for (int offset = 0; ; ) {
            http.checkCollectionDeadline();
            String target = url.apply(offset);
            byte[] bytes = http.download(URI.create(target));
            JsonNode page = parse(bytes, gml);
            if (!"FeatureCollection".equals(page.path("type").asText()) || !page.path("features").isArray())
                throw OfficialDataClient.upstream("Serviço não retornou uma FeatureCollection.");
            long matched = page.path("numberMatched").asLong(page.path("totalFeatures").asLong(-1));
            if (expected < 0) expected = matched;
            if (expected < 0 || expected > limits.getMaxFeatures()) throw OfficialDataClient.upstream("Total ausente ou superior ao limite de feições. Solicite bbox menor.");
            if (matched >= 0 && matched != expected) throw OfficialDataClient.upstream("Base mudou durante a paginação; repita a coleta.");
            var batch = page.path("features");
            if (batch.size() > limits.getPageSize() || offset + batch.size() > expected)
                throw OfficialDataClient.upstream("Paginação inconsistente.");
            for (var feature : batch) {
                String id = feature.path("id").asText();
                if (!"Feature".equals(feature.path("type").asText()) || !feature.path("geometry").isObject()
                        || !feature.path("properties").isObject() || id.isBlank() || !ids.add(id))
                    throw OfficialDataClient.upstream("Feição incompleta ou página repetida.");
                features.add(feature);
            }
            originals.add(new CollectedData.Original("pagina_" + offset + (gml ? ".gml" : ".geojson"), target,
                    gml ? "application/gml+xml" : "application/geo+json", bytes));
            offset += batch.size();
            if (offset == expected) break;
            if (batch.isEmpty()) throw OfficialDataClient.upstream("Paginação interrompida antes do total.");
        }
        return new Result(mapper.writeValueAsBytes(output), List.copyOf(originals));
    }

    private JsonNode parse(byte[] bytes, boolean gml) throws IOException {
        JsonNode root = gml ? GmlFeatureReader.read(bytes) : mapper.readTree(bytes);
        if (root == null || root.has("error")) throw OfficialDataClient.upstream("Serviço oficial retornou erro em vez de dados.");
        return root;
    }
}
