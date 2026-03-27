package ch.so.agi.gdal.ffm;

import ch.so.agi.gdal.ffm.internal.GdalRuntime;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class Gdal {
    private Gdal() {
    }

    public static void vectorTranslate(Path dest, Path src, String... args) {
        vectorTranslate(dest, src, null, args);
    }

    public static void vectorTranslate(Path dest, Path src, ProgressCallback progress, String... args) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        GdalRuntime.vectorTranslate(dest, src, progress, args);
    }

    public static void rasterConvert(Path dest, Path src, String... args) {
        rasterConvert(dest, src, null, args);
    }

    public static void rasterConvert(Path dest, Path src, ProgressCallback progress, String... args) {
        rasterConvert(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), progress, args);
    }

    public static void rasterClip(Path dest, Path src, String... args) {
        rasterClip(dest, src, null, args);
    }

    public static void rasterClip(Path dest, Path src, ProgressCallback progress, String... args) {
        rasterClip(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), progress, args);
    }

    public static void rasterReproject(Path dest, Path src, String... args) {
        rasterReproject(dest, src, null, args);
    }

    public static void rasterReproject(Path dest, Path src, ProgressCallback progress, String... args) {
        rasterReproject(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), progress, args);
    }

    public static void rasterResize(Path dest, Path src, String... args) {
        rasterResize(dest, src, null, args);
    }

    public static void rasterResize(Path dest, Path src, ProgressCallback progress, String... args) {
        rasterResize(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), progress, args);
    }

    public static String rasterInfo(Path src, String... args) {
        return rasterInfo(DatasetRef.local(src), GdalConfig.empty(), args);
    }

    public static String rasterInfo(DatasetRef src, String... args) {
        return rasterInfo(src, GdalConfig.empty(), args);
    }

    public static String rasterInfo(DatasetRef src, GdalConfig config, String... args) {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return GdalRuntime.rasterInfo(src, config, args);
    }

    public static void rasterConvert(DatasetRef dest, DatasetRef src, String... args) {
        rasterConvert(dest, src, GdalConfig.empty(), null, args);
    }

    public static void rasterConvert(DatasetRef dest, DatasetRef src, GdalConfig config, String... args) {
        rasterConvert(dest, src, config, null, args);
    }

    public static void rasterConvert(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.rasterConvert(dest, src, config, progress, args);
    }

    public static void rasterClip(DatasetRef dest, DatasetRef src, String... args) {
        rasterClip(dest, src, GdalConfig.empty(), null, args);
    }

    public static void rasterClip(DatasetRef dest, DatasetRef src, GdalConfig config, String... args) {
        rasterClip(dest, src, config, null, args);
    }

    public static void rasterClip(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.rasterClip(dest, src, config, progress, args);
    }

    public static void rasterReproject(DatasetRef dest, DatasetRef src, String... args) {
        rasterReproject(dest, src, GdalConfig.empty(), null, args);
    }

    public static void rasterReproject(DatasetRef dest, DatasetRef src, GdalConfig config, String... args) {
        rasterReproject(dest, src, config, null, args);
    }

    public static void rasterReproject(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.rasterReproject(dest, src, config, progress, args);
    }

    public static void rasterResize(DatasetRef dest, DatasetRef src, String... args) {
        rasterResize(dest, src, GdalConfig.empty(), null, args);
    }

    public static void rasterResize(DatasetRef dest, DatasetRef src, GdalConfig config, String... args) {
        rasterResize(dest, src, config, null, args);
    }

    public static void rasterResize(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.rasterResize(dest, src, config, progress, args);
    }

    public static void rasterMosaic(Path dest, List<Path> sources, String... args) {
        Objects.requireNonNull(sources, "sources must not be null");
        rasterMosaic(
                DatasetRef.local(dest),
                sources.stream().map(DatasetRef::local).toList(),
                GdalConfig.empty(),
                null,
                args
        );
    }

    public static void rasterMosaic(DatasetRef dest, List<DatasetRef> sources, String... args) {
        rasterMosaic(dest, sources, GdalConfig.empty(), null, args);
    }

    public static void rasterMosaic(
            DatasetRef dest,
            List<DatasetRef> sources,
            GdalConfig config,
            String... args
    ) {
        rasterMosaic(dest, sources, config, null, args);
    }

    public static void rasterMosaic(
            DatasetRef dest,
            List<DatasetRef> sources,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(sources, "sources must not be null");
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.rasterMosaic(dest, sources, config, progress, args);
    }

    public static void vectorRasterize(Path dest, Path src, String... args) {
        vectorRasterize(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), null, args);
    }

    public static void vectorRasterize(DatasetRef dest, DatasetRef src, String... args) {
        vectorRasterize(dest, src, GdalConfig.empty(), null, args);
    }

    public static void vectorRasterize(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            String... args
    ) {
        vectorRasterize(dest, src, config, null, args);
    }

    public static void vectorRasterize(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.vectorRasterize(dest, src, config, progress, args);
    }

    public static List<RasterDriverInfo> listWritableRasterDrivers() {
        return GdalRuntime.listWritableRasterDrivers();
    }

    public static String driverCreationOptionListXml(String driverShortName) {
        Objects.requireNonNull(driverShortName, "driverShortName must not be null");
        return GdalRuntime.driverCreationOptionListXml(driverShortName);
    }

    public static List<String> listCreationOptionEnumValues(String driverShortName, String optionName) {
        Objects.requireNonNull(driverShortName, "driverShortName must not be null");
        Objects.requireNonNull(optionName, "optionName must not be null");
        return GdalRuntime.listCreationOptionEnumValues(driverShortName, optionName);
    }

    public static List<String> listCompressionOptions(String driverShortName) {
        Objects.requireNonNull(driverShortName, "driverShortName must not be null");
        return GdalRuntime.listCompressionOptions(driverShortName);
    }
}
