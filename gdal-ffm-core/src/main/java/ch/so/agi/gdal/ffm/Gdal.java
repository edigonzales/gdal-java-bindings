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

    public static void warp(Path dest, Path src, String... args) {
        warp(dest, src, null, args);
    }

    public static void warp(Path dest, Path src, ProgressCallback progress, String... args) {
        warp(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), progress, args);
    }

    public static void translate(Path dest, Path src, String... args) {
        translate(dest, src, null, args);
    }

    public static void translate(Path dest, Path src, ProgressCallback progress, String... args) {
        translate(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), progress, args);
    }

    public static String info(Path src, String... args) {
        return info(DatasetRef.local(src), GdalConfig.empty(), args);
    }

    public static String info(DatasetRef src, String... args) {
        return info(src, GdalConfig.empty(), args);
    }

    public static String info(DatasetRef src, GdalConfig config, String... args) {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return GdalRuntime.info(src, config, args);
    }

    public static void warp(DatasetRef dest, DatasetRef src, String... args) {
        warp(dest, src, GdalConfig.empty(), null, args);
    }

    public static void warp(DatasetRef dest, DatasetRef src, GdalConfig config, String... args) {
        warp(dest, src, config, null, args);
    }

    public static void warp(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.warp(dest, src, config, progress, args);
    }

    public static void translate(DatasetRef dest, DatasetRef src, String... args) {
        translate(dest, src, GdalConfig.empty(), null, args);
    }

    public static void translate(DatasetRef dest, DatasetRef src, GdalConfig config, String... args) {
        translate(dest, src, config, null, args);
    }

    public static void translate(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.translate(dest, src, config, progress, args);
    }

    public static void buildVrt(Path dest, List<Path> sources, String... args) {
        Objects.requireNonNull(sources, "sources must not be null");
        buildVrt(DatasetRef.local(dest), sources.stream().map(DatasetRef::local).toList(), GdalConfig.empty(), null, args);
    }

    public static void buildVrt(DatasetRef dest, List<DatasetRef> sources, String... args) {
        buildVrt(dest, sources, GdalConfig.empty(), null, args);
    }

    public static void buildVrt(
            DatasetRef dest,
            List<DatasetRef> sources,
            GdalConfig config,
            String... args
    ) {
        buildVrt(dest, sources, config, null, args);
    }

    public static void buildVrt(
            DatasetRef dest,
            List<DatasetRef> sources,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(sources, "sources must not be null");
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.buildVrt(dest, sources, config, progress, args);
    }

    public static void rasterize(Path dest, Path src, String... args) {
        rasterize(DatasetRef.local(dest), DatasetRef.local(src), GdalConfig.empty(), null, args);
    }

    public static void rasterize(DatasetRef dest, DatasetRef src, String... args) {
        rasterize(dest, src, GdalConfig.empty(), null, args);
    }

    public static void rasterize(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            String... args
    ) {
        rasterize(dest, src, config, null, args);
    }

    public static void rasterize(
            DatasetRef dest,
            DatasetRef src,
            GdalConfig config,
            ProgressCallback progress,
            String... args
    ) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.rasterize(dest, src, config, progress, args);
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
