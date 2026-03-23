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
        return OgrRuntime.open(DatasetRef.local(path), openOptions, GdalConfig.empty());
    }

    public static OgrDataSource open(DatasetRef datasetRef) {
        return open(datasetRef, Map.of(), GdalConfig.empty());
    }

    public static OgrDataSource open(DatasetRef datasetRef, Map<String, String> openOptions) {
        return open(datasetRef, openOptions, GdalConfig.empty());
    }

    public static OgrDataSource open(
            DatasetRef datasetRef,
            Map<String, String> openOptions,
            GdalConfig config
    ) {
        Objects.requireNonNull(datasetRef, "datasetRef must not be null");
        Objects.requireNonNull(openOptions, "openOptions must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return OgrRuntime.open(datasetRef, openOptions, config);
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
        return create(DatasetRef.local(path), driverShortName, writeMode, datasetCreationOptions, GdalConfig.empty());
    }

    public static OgrDataSource create(
            DatasetRef datasetRef,
            String driverShortName,
            OgrWriteMode writeMode
    ) {
        return create(datasetRef, driverShortName, writeMode, Map.of(), GdalConfig.empty());
    }

    public static OgrDataSource create(
            DatasetRef datasetRef,
            String driverShortName,
            OgrWriteMode writeMode,
            Map<String, String> datasetCreationOptions
    ) {
        return create(datasetRef, driverShortName, writeMode, datasetCreationOptions, GdalConfig.empty());
    }

    public static OgrDataSource create(
            DatasetRef datasetRef,
            String driverShortName,
            OgrWriteMode writeMode,
            Map<String, String> datasetCreationOptions,
            GdalConfig config
    ) {
        Objects.requireNonNull(datasetRef, "datasetRef must not be null");
        Objects.requireNonNull(driverShortName, "driverShortName must not be null");
        Objects.requireNonNull(writeMode, "writeMode must not be null");
        Objects.requireNonNull(datasetCreationOptions, "datasetCreationOptions must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return OgrRuntime.create(datasetRef, driverShortName, writeMode, datasetCreationOptions, config);
    }

    public static List<OgrDriverInfo> listWritableVectorDrivers() {
        return OgrRuntime.listWritableVectorDrivers();
    }
}
