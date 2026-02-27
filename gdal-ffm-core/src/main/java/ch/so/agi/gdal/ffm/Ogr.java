package ch.so.agi.gdal.ffm;

import ch.so.agi.gdal.ffm.internal.OgrRuntime;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * OGR vector streaming entrypoints.
 */
public final class Ogr {
    private Ogr() {
    }

    public static OgrDataSource open(Path path) {
        return open(path, Map.of());
    }

    public static OgrDataSource open(Path path, Map<String, String> openOptions) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(openOptions, "openOptions must not be null");
        return OgrRuntime.open(path, openOptions);
    }
}
