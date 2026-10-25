package catdevs.georuraldatahub.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import catdevs.georuraldatahub.entity.*;
import catdevs.georuraldatahub.repository.*;
import catdevs.georuraldatahub.repository.FeatureRepository.NewFeature;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FeatureServiceTest {
    @Test void mapsLargeGeometryAndAttributesToJpaWithoutTruncatingClobData() {
        var repository = mock(FeatureRepository.class); var layers = mock(LayerRepository.class);
        var layer = new Layer(); layer.setId(10L); when(layers.getReferenceById(10L)).thenReturn(layer);
        var geometry = new org.locationtech.jts.geom.GeometryFactory().createPoint(new org.locationtech.jts.geom.Coordinate(-51, -25)).buffer(0.01, 200);
        var writer = new org.locationtech.jts.io.geojson.GeoJsonWriter(); writer.setEncodeCRS(false);
        String json = writer.write(geometry); assertTrue(json.length() > 4000);
        String properties = "{\"texto\":\"" + "á".repeat(3000) + "\"}";
        int total = new FeatureService(repository, layers, mock(jakarta.persistence.EntityManager.class)).insertBatch(10L, 4326, List.of(new NewFeature("origem-1", json, properties)));
        assertEquals(1, total);
        @SuppressWarnings("unchecked") var captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAllAndFlush(captor.capture());
        Feature feature = (Feature) captor.getValue().getFirst();
        assertSame(layer, feature.getLayer()); assertEquals(4326, feature.getGeometry().getSRID());
        assertEquals(geometry.getNumPoints(), feature.getGeometry().getNumPoints()); assertEquals(properties, feature.getAttributes());
    }

    @Test void flushesAndDetachesAtMostFiveHundredFeaturesAtATime() {
        var repository = mock(FeatureRepository.class);
        var layers = mock(LayerRepository.class);
        var manager = mock(jakarta.persistence.EntityManager.class);
        var feature = new NewFeature("1", "{\"type\":\"Point\",\"coordinates\":[-51,-25]}", "{}");
        var service = new FeatureService(repository, layers, manager);
        java.util.List<Integer> sizes = new ArrayList<>();
        doAnswer(call -> {
            List<?> batch = call.getArgument(0);
            sizes.add(batch.size());
            return batch;
        }).when(repository).saveAllAndFlush(anyList());
        assertEquals(1001, service.insertBatch(1L, 4326, Collections.nCopies(1001, feature)));
        assertEquals(List.of(500, 500, 1), sizes);
        verify(manager, times(1001)).detach(any(Feature.class));
        verify(manager, never()).clear();
        var order = inOrder(repository, manager);
        order.verify(repository).saveAllAndFlush(anyList());
        order.verify(manager, times(500)).detach(any(Feature.class));
        order.verify(repository).saveAllAndFlush(anyList());
    }
}
