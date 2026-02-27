package ch.so.agi.gdal.ffm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OgrIntegrationTest {
    @Test
    void listsLayersAndReadsWithAttributeProjectionAndLimit() throws Exception {
        Path geoJson = createTempGeoJson();
        try (OgrDataSource dataSource = Ogr.open(geoJson)) {
            List<OgrLayerDefinition> layers = dataSource.listLayers();
            assertFalse(layers.isEmpty(), "Expected at least one layer in datasource");

            String layerName = layers.get(0).name();
            try (OgrLayerReader reader = dataSource.openReader(layerName, Map.of(
                    OgrReaderOptions.ATTRIBUTE_FILTER, "value >= 2",
                    OgrReaderOptions.SELECTED_FIELDS, "name,value",
                    OgrReaderOptions.LIMIT, "1"
            ))) {
                List<OgrFeature> features = collect(reader);
                assertEquals(1, features.size());

                OgrFeature first = features.get(0);
                assertEquals(2, first.attributes().size());
                assertTrue(first.attributes().containsKey("name"));
                assertTrue(first.attributes().containsKey("value"));
                assertNotNull(first.geometry());
            }
        } finally {
            Files.deleteIfExists(geoJson);
        }
    }

    @Test
    void appliesBboxFilter() throws Exception {
        Path geoJson = createTempGeoJson();
        try (OgrDataSource dataSource = Ogr.open(geoJson)) {
            String layerName = dataSource.listLayers().getFirst().name();
            try (OgrLayerReader reader = dataSource.openReader(layerName, Map.of(
                    OgrReaderOptions.BBOX, "14,14,16,16"
            ))) {
                List<OgrFeature> features = collect(reader);
                assertEquals(1, features.size());
                assertEquals("B", features.getFirst().attributes().get("name"));
            }
        } finally {
            Files.deleteIfExists(geoJson);
        }
    }

    @Test
    void rejectsUnknownSelectedField() throws Exception {
        Path geoJson = createTempGeoJson();
        try (OgrDataSource dataSource = Ogr.open(geoJson)) {
            String layerName = dataSource.listLayers().getFirst().name();
            assertThrows(IllegalArgumentException.class, () ->
                    dataSource.openReader(layerName, Map.of(
                            OgrReaderOptions.SELECTED_FIELDS, "name,missing_field"
                    )));
        } finally {
            Files.deleteIfExists(geoJson);
        }
    }

    private static List<OgrFeature> collect(OgrLayerReader reader) {
        List<OgrFeature> features = new ArrayList<>();
        for (OgrFeature feature : reader) {
            features.add(feature);
        }
        return features;
    }

    private static Path createTempGeoJson() throws Exception {
        Path geoJson = Files.createTempFile("ogr-integration-", ".geojson");
        Files.writeString(geoJson, """
                {
                  "type": "FeatureCollection",
                  "features": [
                    { "type": "Feature", "properties": { "name": "A", "value": 1 }, "geometry": { "type": "Point", "coordinates": [5, 5] } },
                    { "type": "Feature", "properties": { "name": "B", "value": 2 }, "geometry": { "type": "Point", "coordinates": [15, 15] } },
                    { "type": "Feature", "properties": { "name": "C", "value": 3 }, "geometry": { "type": "Point", "coordinates": [25, 25] } }
                  ]
                }
                """);
        return geoJson;
    }
}
