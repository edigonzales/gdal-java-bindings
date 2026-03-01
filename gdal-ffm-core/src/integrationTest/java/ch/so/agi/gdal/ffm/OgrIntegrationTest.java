package ch.so.agi.gdal.ffm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OgrIntegrationTest {
    private static final String DRIVER_GPKG = "GPKG";
    private static final int GEOMETRY_TYPE_POINT = 1;

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

    @Test
    void listsWritableVectorDrivers() {
        List<OgrDriverInfo> drivers = Ogr.listWritableVectorDrivers();
        assertFalse(drivers.isEmpty(), "Expected at least one writable vector driver");
        assertTrue(drivers.stream().allMatch(OgrDriverInfo::isVector));
        assertTrue(drivers.stream().allMatch(OgrDriverInfo::canCreate));
    }

    @Test
    void writesAndReadsRoundTripWithGpkg() throws Exception {
        assumeGpkgDriver();

        Path output = createTempOutputPath("gpkg");
        List<OgrFieldDefinition> schema = defaultSchema();

        try {
            try (OgrDataSource dataSource = Ogr.create(output, DRIVER_GPKG, OgrWriteMode.FAIL_IF_EXISTS);
                 OgrLayerWriter writer = dataSource.openWriter(new OgrLayerWriteSpec("features", GEOMETRY_TYPE_POINT, schema))) {
                writer.write(feature(1L, "A", 100L, 5, 5));
                writer.write(feature(2L, "B", 200L, 15, 15));
            }

            try (OgrDataSource readDataSource = Ogr.open(output)) {
                String layer = readDataSource.listLayers().getFirst().name();
                try (OgrLayerReader reader = readDataSource.openReader(layer, Map.of())) {
                    List<OgrFeature> features = collect(reader);
                    assertEquals(2, features.size());
                    assertEquals("A", features.getFirst().attributes().get("name"));
                }
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void appendsToExistingLayer() throws Exception {
        assumeGpkgDriver();

        Path output = createTempOutputPath("gpkg");
        List<OgrFieldDefinition> schema = defaultSchema();

        try {
            try (OgrDataSource dataSource = Ogr.create(output, DRIVER_GPKG, OgrWriteMode.FAIL_IF_EXISTS);
                 OgrLayerWriter writer = dataSource.openWriter(new OgrLayerWriteSpec("features", GEOMETRY_TYPE_POINT, schema))) {
                writer.write(feature(1L, "A", 100L, 5, 5));
            }

            OgrLayerWriteSpec appendSpec = new OgrLayerWriteSpec(
                    "features",
                    null,
                    schema,
                    OgrWriteMode.APPEND,
                    Map.of(),
                    Map.of(),
                    null,
                    null
            );
            try (OgrDataSource dataSource = Ogr.create(output, DRIVER_GPKG, OgrWriteMode.APPEND);
                 OgrLayerWriter writer = dataSource.openWriter(appendSpec)) {
                writer.write(feature(2L, "B", 200L, 15, 15));
            }

            try (OgrDataSource readDataSource = Ogr.open(output);
                 OgrLayerReader reader = readDataSource.openReader("features", Map.of())) {
                assertEquals(2, collect(reader).size());
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void overwritesExistingDataset() throws Exception {
        assumeGpkgDriver();

        Path output = createTempOutputPath("gpkg");
        List<OgrFieldDefinition> schema = defaultSchema();

        try {
            try (OgrDataSource dataSource = Ogr.create(output, DRIVER_GPKG, OgrWriteMode.FAIL_IF_EXISTS);
                 OgrLayerWriter writer = dataSource.openWriter(new OgrLayerWriteSpec("features", GEOMETRY_TYPE_POINT, schema))) {
                writer.write(feature(1L, "A", 100L, 5, 5));
            }

            try (OgrDataSource dataSource = Ogr.create(output, DRIVER_GPKG, OgrWriteMode.OVERWRITE);
                 OgrLayerWriter writer = dataSource.openWriter(new OgrLayerWriteSpec("features", GEOMETRY_TYPE_POINT, schema))) {
                writer.write(feature(2L, "B", 200L, 15, 15));
            }

            try (OgrDataSource readDataSource = Ogr.open(output);
                 OgrLayerReader reader = readDataSource.openReader("features", Map.of())) {
                List<OgrFeature> features = collect(reader);
                assertEquals(1, features.size());
                assertEquals("B", features.getFirst().attributes().get("name"));
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void failsIfCreateTargetAlreadyExists() throws Exception {
        assumeGpkgDriver();

        Path output = createTempOutputPath("gpkg");
        try {
            Files.writeString(output, "existing");
            assertThrows(IllegalArgumentException.class, () ->
                    Ogr.create(output, DRIVER_GPKG, OgrWriteMode.FAIL_IF_EXISTS));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static void assumeGpkgDriver() {
        boolean gpkgPresent = Ogr.listWritableVectorDrivers().stream()
                .map(OgrDriverInfo::shortName)
                .anyMatch(DRIVER_GPKG::equals);
        assumeTrue(gpkgPresent, "GPKG writable driver is required for writer integration tests");
    }

    private static OgrFeature feature(long fid, String name, long id, double x, double y) {
        return new OgrFeature(
                fid,
                Map.of(
                        "id", id,
                        "name", name
                ),
                OgrGeometry.fromWkb(pointWkb(x, y), 2056)
        );
    }

    private static List<OgrFieldDefinition> defaultSchema() {
        return List.of(
                new OgrFieldDefinition("id", OgrFieldType.INTEGER64),
                new OgrFieldDefinition("name", OgrFieldType.STRING)
        );
    }

    private static byte[] pointWkb(double x, double y) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + 8 + 8).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 1);
        buffer.putInt(1);
        buffer.putDouble(x);
        buffer.putDouble(y);
        return buffer.array();
    }

    private static List<OgrFeature> collect(OgrLayerReader reader) {
        List<OgrFeature> features = new ArrayList<>();
        for (OgrFeature feature : reader) {
            features.add(feature);
        }
        return features;
    }

    private static Path createTempOutputPath(String extension) throws Exception {
        Path temp = Files.createTempFile("ogr-integration-write-", "." + extension);
        Files.deleteIfExists(temp);
        return temp;
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
