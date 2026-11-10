package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GmlFeatureReaderTest {
    private String collection(String members) {
        return "<wfs:FeatureCollection xmlns:wfs='http://www.opengis.net/wfs/2.0' xmlns:ms='http://mapserver.gis.umn.edu/mapserver'"
                + " xmlns:gml='http://www.opengis.net/gml/3.2' numberMatched='2'>" + members + "</wfs:FeatureCollection>";
    }
    private String feature(String positions) {
        return "<wfs:member><ms:ucs_selected><ms:uc_id>1</ms:uc_id><ms:docleg_id>2</ms:docleg_id>"
                + "<ms:msGeometry><gml:Polygon srsName='urn:ogc:def:crs:EPSG::4326'><gml:exterior><gml:LinearRing>"
                + "<gml:posList srsDimension='2'>" + positions + "</gml:posList></gml:LinearRing></gml:exterior>"
                + "</gml:Polygon></ms:msGeometry></ms:ucs_selected></wfs:member>";
    }
    @Test void swapsLatitudeLongitudeAndPreservesDistinctPolygonsOfSameUnit() throws Exception {
        var root = GmlFeatureReader.read(collection(feature("-25 -50 -25 -49 -24 -49 -25 -50")
                + feature("-26 -50 -26 -49 -25 -49 -26 -50")).getBytes(StandardCharsets.UTF_8));
        var features = root.path("features");
        assertEquals(2, features.size());
        var first = features.get(0).path("geometry").path("coordinates").get(0).get(0).get(0);
        assertEquals(-50,first.get(0).asDouble()); assertEquals(-25,first.get(1).asDouble());
        assertNotEquals(features.get(0).path("id"), features.get(1).path("id"));
    }
    @Test void rejectsExternalEntitiesAndUnexpectedGeometries() {
        String xml = "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]>" + collection("&e;");
        assertThrows(IOException.class, () -> GmlFeatureReader.read(xml.getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> GmlFeatureReader.read(collection("<wfs:member><ms:x/></wfs:member>").getBytes(StandardCharsets.UTF_8)));
        String wrongCrs = collection(feature("-25 -50 -25 -49 -24 -49 -25 -50")).replace("urn:ogc:def:crs:EPSG::4326", "CRS:84");
        assertThrows(IOException.class, () -> GmlFeatureReader.read(wrongCrs.getBytes(StandardCharsets.UTF_8)));
    }
}
