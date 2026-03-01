package ch.so.agi.gdal.ffm;

import ch.so.agi.gdal.ffm.internal.OgrRuntime;
import java.nio.file.Path;
import java.util.List;
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

    public static OgrDataSource create(Path path, String driverShortName, OgrWriteMode writeMode) {
        return create(path, driverShortName, writeMode, Map.of());
    }

    public static OgrDataSource create(
            Path path,
            String driverShortName,
            OgrWriteMode writeMode,
            Map<String, String> datasetCreationOptions
    ) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(driverShortName, "driverShortName must not be null");
        Objects.requireNonNull(writeMode, "writeMode must not be null");
        Objects.requireNonNull(datasetCreationOptions, "datasetCreationOptions must not be null");
        return OgrRuntime.create(path, driverShortName, writeMode, datasetCreationOptions);
    }

    public static List<OgrDriverInfo> listWritableVectorDrivers() {
        return OgrRuntime.listWritableVectorDrivers();
    }
}
