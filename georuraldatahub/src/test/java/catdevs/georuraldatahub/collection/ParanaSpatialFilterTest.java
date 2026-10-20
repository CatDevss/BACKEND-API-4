package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import com.fasterxml.jackson.databind.ObjectMapper;

class ParanaSpatialFilterTest {
    private final OfficialDataClient http = mock(OfficialDataClient.class);
    private final ParanaSpatialFilter filter = new ParanaSpatialFilter(http);
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void clipsCrossingPolygonsAndExcludesOutsideAndBoundaryOnlyContacts() throws Exception {
        Geometry boundary = new GeoJsonReader().read("{\"type\":\"Polygon\",\"coordinates\":[[[-51,-25],[-49,-25],[-49,-23],[-51,-23],[-51,-25]]]}");
        var input = mapper.createObjectNode().put("type","FeatureCollection");
        var features = input.putArray("features");
        double[][] extents = {{-50.5,-24.5,-49.5,-23.5},{-52,-24.5,-50,-23.5},{-54,-25,-53,-24},{-52,-25,-51,-23}};
        for (int i=0;i<extents.length;i++) {
            double[] e=extents[i];
            var feature=features.addObject().put("type","Feature");feature.putObject("properties").put("id",i);
            String geometry="{\"type\":\"Polygon\",\"coordinates\":[[["+e[0]+","+e[1]+"],["+e[2]+","+e[1]+"],["+e[2]+","+e[3]+"],["+e[0]+","+e[3]+"],["+e[0]+","+e[1]+"]]]}";
            feature.set("geometry",mapper.readTree(geometry));
        }
        var result=mapper.readTree(filter.clip(mapper.writeValueAsBytes(input),new ParanaSpatialFilter.Boundary(boundary,null)));
        assertEquals(2,result.path("features").size());
        for(var feature:result.path("features")) {
            Geometry geometry=new GeoJsonReader().read(feature.path("geometry").toString());
            assertTrue(boundary.covers(geometry));assertTrue(geometry.getArea()>0);
        }
    }
    @Test void verifiesOfficialUf41Boundary() throws Exception {
        when(http.download(any())).thenReturn(PublicDataConnectorsTest.fixture("ibge-parana-2022.geojson"));
        var boundary=filter.boundary();
        assertFalse(boundary.geometry().isEmpty());
        assertTrue(boundary.original().origin().contains("/estados/PR"));
    }
    @Test void rejectsDifferentStateInsteadOfClippingToWrongRegion() throws Exception {
        when(http.download(any())).thenReturn("{\"type\":\"FeatureCollection\",\"features\":[{\"properties\":{\"codarea\":\"35\"}}]}".getBytes(StandardCharsets.UTF_8));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,filter::boundary);
    }
}
