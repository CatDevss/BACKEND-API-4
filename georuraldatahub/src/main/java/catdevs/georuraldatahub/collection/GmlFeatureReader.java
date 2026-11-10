package catdevs.georuraldatahub.collection;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Subconjunto GML 3.2 publicado pelo CNUC, solicitado explicitamente em EPSG:4326 (lat/lon). */
final class GmlFeatureReader {
    private static final String GML = "http://www.opengis.net/gml/3.2";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static ObjectNode read(byte[] bytes) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            if (!"FeatureCollection".equals(root.getLocalName())) throw new IOException("WFS não retornou FeatureCollection GML.");
            var result = MAPPER.createObjectNode().put("type", "FeatureCollection");
            String matched = root.getAttribute("numberMatched");
            if (matched.matches("[0-9]+")) result.put("numberMatched", Long.parseLong(matched));
            var features = result.putArray("features");
            var members = root.getElementsByTagNameNS("http://www.opengis.net/wfs/2.0", "member");
            for (int i = 0; i < members.getLength(); i++) {
                var member = (Element) members.item(i);
                Element source = child(member);
                var feature = features.addObject().put("type", "Feature");
                var properties = feature.putObject("properties");
                for (Node n = source.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (!(n instanceof Element field) || GML.equals(field.getNamespaceURI())) continue;
                    if (!field.getLocalName().equals("msGeometry")) properties.put(field.getLocalName(), field.getTextContent());
                }
                var geometry = feature.putObject("geometry").put("type", "MultiPolygon");
                var polygons = geometry.putArray("coordinates");
                var nodes = source.getElementsByTagNameNS(GML, "Polygon");
                if (nodes.getLength() == 0) throw new IOException("CNUC retornou geometria não poligonal.");
                for (int p = 0; p < nodes.getLength(); p++) {
                    Element polygon = (Element) nodes.item(p);
                    String crs = polygon.getAttribute("srsName");
                    for (Node parent = polygon.getParentNode(); crs.isBlank() && parent instanceof Element element; parent = parent.getParentNode())
                        crs = element.getAttribute("srsName");
                    if (!"urn:ogc:def:crs:EPSG::4326".equals(crs)) throw new IOException("CNUC ignorou o sistema de coordenadas solicitado.");
                    var rings = polygons.addArray();
                    for (String role : new String[]{"exterior", "interior"}) {
                        var boundaries = polygon.getElementsByTagNameNS(GML, role);
                        if (role.equals("exterior") && boundaries.getLength() != 1) throw new IOException("Contorno GML ausente ou ambíguo.");
                        for (int r = 0; r < boundaries.getLength(); r++) {
                            var positions = ((Element) boundaries.item(r)).getElementsByTagNameNS(GML, "posList");
                            if (positions.getLength() != 1) throw new IOException("Coordenadas GML ausentes.");
                            Element pos = (Element) positions.item(0);
                            if (!pos.getAttribute("srsDimension").isEmpty() && !pos.getAttribute("srsDimension").equals("2"))
                                throw new IOException("Dimensão GML não suportada.");
                            String[] numbers = pos.getTextContent().trim().split("\\s+");
                            if (numbers.length < 8 || numbers.length % 2 != 0) throw new IOException("Anel GML incompleto.");
                            var ring = rings.addArray();
                            for (int j = 0; j < numbers.length; j += 2) {
                                double lat = Double.parseDouble(numbers[j]), lon = Double.parseDouble(numbers[j + 1]);
                                if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180)
                                    throw new IOException("Coordenadas GML fora dos limites.");
                                ring.addArray().add(lon).add(lat);
                            }
                        }
                    }
                }
                // Uma UC/ato pode ter mais de um polígono; a identidade precisa incluir a geometria.
                feature.put("id", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(MAPPER.writeValueAsBytes(feature))));
            }
            return result;
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("Resposta GML inválida.", e); }
    }

    private static Element child(Element parent) throws IOException {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element element) return element;
        throw new IOException("Feição GML vazia.");
    }
}
