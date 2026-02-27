package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.OgrDataSource;
import ch.so.agi.gdal.ffm.OgrLayerReader;
import ch.so.agi.gdal.ffm.OgrLayerWriter;
import java.nio.file.Path;
import java.util.Map;

public final class OgrRuntime {
    private OgrRuntime() {
    }

    public static OgrDataSource open(Path path, Map<String, String> openOptions) {
        // Temporary compile-safe placeholder until OGR symbols are generated and wired.
        return new UnsupportedOgrDataSource(path, openOptions);
    }

    private record UnsupportedOgrDataSource(Path path, Map<String, String> openOptions) implements OgrDataSource {
        @Override
        public OgrLayerReader openReader(String layerName, Map<String, String> options) {
            throw unsupported("read", layerName);
        }

        @Override
        public OgrLayerWriter openWriter(String layerName, Map<String, String> options) {
            throw unsupported("write", layerName);
        }

        @Override
        public void close() {
            // no-op
        }

        private UnsupportedOperationException unsupported(String mode, String layerName) {
            return new UnsupportedOperationException(
                    "OGR " + mode + " API is not wired yet for datasource '" + path + "'"
                            + (layerName == null ? "" : (", layer '" + layerName + "'"))
                            + ". Regenerate FFM bindings with OGR includes and implement OgrRuntime native calls."
            );
        }
    }

}
