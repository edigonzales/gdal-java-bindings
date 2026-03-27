package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.DatasetRef;
import ch.so.agi.gdal.ffm.GdalConfig;
import ch.so.agi.gdal.ffm.ProgressCallback;
import ch.so.agi.gdal.ffm.RasterDriverInfo;
import ch.so.agi.gdal.ffm.generated.GdalGenerated;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GdalRuntime {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Object INIT_LOCK = new Object();

    private static final int GDAL_OF_RASTER = 0x02;
    private static final int GDAL_OF_VECTOR = 0x04;
    private static final int GDAL_OF_VERBOSE_ERROR = 0x40;
    private static final String MD_DCAP_RASTER = "DCAP_RASTER";
    private static final String MD_DCAP_CREATE = "DCAP_CREATE";
    private static final String MD_DCAP_CREATECOPY = "DCAP_CREATECOPY";
    private static final String MD_DMD_EXTENSIONS = "DMD_EXTENSIONS";
    private static final String MD_DMD_EXTENSION = "DMD_EXTENSION";
    private static final String MD_DMD_CREATIONOPTIONLIST = "DMD_CREATIONOPTIONLIST";

    private GdalRuntime() {
    }

    public static void vectorTranslate(Path dest, Path src, ProgressCallback progress, String... args) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        initialize();

        MemorySegment options = MemorySegment.NULL;
        MemorySegment sourceDataset = MemorySegment.NULL;
        MemorySegment resultDataset = MemorySegment.NULL;

        GdalGenerated.CPLErrorReset();
        try (Arena arena = Arena.ofConfined();
             ProgressBridge.ProgressHandle progressHandle = ProgressBridge.create(progress, arena)) {
            MemorySegment argv = CArgv.toCStringArray(args, arena);
            options = GdalGenerated.GDALVectorTranslateOptionsNew(argv, MemorySegment.NULL);
            if (CStrings.isNull(options)) {
                throw GdalErrors.lastError("Failed to create GDALVectorTranslate options");
            }

            if (!CStrings.isNull(progressHandle.callbackFn())) {
                GdalGenerated.GDALVectorTranslateOptionsSetProgress(options, progressHandle.callbackFn(), progressHandle.userData());
            }

            sourceDataset = openDataset(DatasetRef.local(src), GDAL_OF_VECTOR | GDAL_OF_VERBOSE_ERROR, arena);

            MemorySegment sources = arena.allocate(ValueLayout.ADDRESS);
            sources.set(ValueLayout.ADDRESS, 0, sourceDataset);

            MemorySegment usageError = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment destination = arena.allocateFrom(dest.toAbsolutePath().toString());

            resultDataset = GdalGenerated.GDALVectorTranslate(
                    destination,
                    MemorySegment.NULL,
                    1,
                    sources,
                    options,
                    usageError
            );

            throwIfCallbackFailed(progressHandle);

            if (usageError.get(ValueLayout.JAVA_INT, 0) != 0 || CStrings.isNull(resultDataset)) {
                throw GdalErrors.lastError("GDALVectorTranslate failed");
            }
        } finally {
            freeQuietly(options, GdalGenerated::GDALVectorTranslateOptionsFree);
            closeDatasetQuietly(resultDataset);
            closeDatasetQuietly(sourceDataset);
        }
    }

    public static String rasterInfo(DatasetRef src, GdalConfig config, String... args) {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(config, "config must not be null");
        initialize();
        return GdalAlgorithmRunner.runForStringOutput(
                List.of("raster", "info"),
                config,
                null,
                withInputArg(src, args)
        );
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
        initialize();
        GdalAlgorithmRunner.run(List.of("raster", "clip"), config, progress, withInputOutputArgs(src, dest, args));
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
        initialize();
        GdalAlgorithmRunner.run(List.of("raster", "convert"), config, progress, withInputOutputArgs(src, dest, args));
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
        initialize();
        GdalAlgorithmRunner.run(
                List.of("raster", "reproject"),
                config,
                progress,
                withInputOutputArgs(src, dest, args)
        );
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
        initialize();
        GdalAlgorithmRunner.run(List.of("raster", "resize"), config, progress, withInputOutputArgs(src, dest, args));
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
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }

        initialize();
        GdalAlgorithmRunner.run(
                List.of("raster", "mosaic"),
                config,
                progress,
                withInputOutputArgs(sources, dest, args)
        );
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
        initialize();
        GdalAlgorithmRunner.run(List.of("vector", "rasterize"), config, progress, withInputOutputArgs(src, dest, args));
    }

    public static List<RasterDriverInfo> listWritableRasterDrivers() {
        initialize();

        int driverCount = GdalGenerated.GDALGetDriverCount();
        if (driverCount <= 0) {
            return List.of();
        }

        List<RasterDriverInfo> writableDrivers = new ArrayList<>();
        try (Arena arena = Arena.ofConfined()) {
            for (int i = 0; i < driverCount; i++) {
                MemorySegment driver = GdalGenerated.GDALGetDriver(i);
                if (CStrings.isNull(driver) || !isMetadataTrue(driver, MD_DCAP_RASTER, arena)) {
                    continue;
                }

                boolean canCreate = isMetadataTrue(driver, MD_DCAP_CREATE, arena);
                boolean canCreateCopy = isMetadataTrue(driver, MD_DCAP_CREATECOPY, arena);
                if (!canCreate && !canCreateCopy) {
                    continue;
                }

                String shortName = CStrings.fromCString(GdalGenerated.GDALGetDriverShortName(driver)).trim();
                if (shortName.isEmpty()) {
                    continue;
                }
                String longName = CStrings.fromCString(GdalGenerated.GDALGetDriverLongName(driver)).trim();
                if (longName.isEmpty()) {
                    longName = shortName;
                }

                String extensionMetadata = readMetadataItem(driver, MD_DMD_EXTENSIONS, arena);
                if (extensionMetadata.isBlank()) {
                    extensionMetadata = readMetadataItem(driver, MD_DMD_EXTENSION, arena);
                }
                List<String> extensions = parseExtensions(extensionMetadata);

                writableDrivers.add(
                        new RasterDriverInfo(shortName, longName, extensions, canCreate, canCreateCopy)
                );
            }
        }

        writableDrivers.sort((left, right) -> left.shortName().compareToIgnoreCase(right.shortName()));
        return List.copyOf(writableDrivers);
    }

    public static String driverCreationOptionListXml(String driverShortName) {
        Objects.requireNonNull(driverShortName, "driverShortName must not be null");
        initialize();
        String normalized = driverShortName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("driverShortName must not be blank");
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment driver = resolveRasterDriver(normalized, arena);
            return readMetadataItem(driver, MD_DMD_CREATIONOPTIONLIST, arena);
        }
    }

    public static List<String> listCreationOptionEnumValues(String driverShortName, String optionName) {
        Objects.requireNonNull(optionName, "optionName must not be null");
        return CreationOptionListParser.enumValues(driverCreationOptionListXml(driverShortName), optionName);
    }

    public static List<String> listCompressionOptions(String driverShortName) {
        return listCreationOptionEnumValues(driverShortName, "COMPRESS");
    }

    public static void initialize() {
        if (INITIALIZED.get()) {
            return;
        }

        synchronized (INIT_LOCK) {
            if (INITIALIZED.get()) {
                return;
            }

            NativeAccess.ensureEnabled();
            NativeBundleInfo bundleInfo = NativeLoader.load();
            applyConfig(bundleInfo);
            GdalGenerated.GDALAllRegister();
            INITIALIZED.set(true);
        }
    }

    static MemorySegment openDataset(DatasetRef src, int flags, Arena arena) {
        MemorySegment sourcePath = arena.allocateFrom(src.toGdalIdentifier());
        MemorySegment dataset = GdalGenerated.GDALOpenEx(
                sourcePath,
                flags,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL
        );
        if (CStrings.isNull(dataset)) {
            throw GdalErrors.lastError("Failed to open source dataset: " + src.identifier());
        }
        return dataset;
    }

    private static MemorySegment resolveRasterDriver(String driverShortName, Arena arena) {
        MemorySegment driverName = arena.allocateFrom(driverShortName);
        MemorySegment driver = GdalGenerated.GDALGetDriverByName(driverName);
        if (!CStrings.isNull(driver)) {
            return driver;
        }
        List<String> available = listWritableRasterDrivers().stream().map(RasterDriverInfo::shortName).toList();
        throw new IllegalArgumentException(
                "Raster driver not found or not writable: '" + driverShortName + "'. Available drivers: " + available
        );
    }

    private static String readMetadataItem(MemorySegment majorObject, String key, Arena arena) {
        MemorySegment keyCString = arena.allocateFrom(key);
        return CStrings.fromCString(GdalGenerated.GDALGetMetadataItem(majorObject, keyCString, MemorySegment.NULL)).trim();
    }

    private static boolean isMetadataTrue(MemorySegment majorObject, String key, Arena arena) {
        String value = readMetadataItem(majorObject, key, arena);
        return "YES".equalsIgnoreCase(value) || "TRUE".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static List<String> parseExtensions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> extensions = new LinkedHashSet<>();
        String[] split = raw.split("[,;\\s]+");
        for (String extension : split) {
            if (extension == null) {
                continue;
            }
            String trimmed = extension.trim();
            if (!trimmed.isEmpty()) {
                extensions.add(trimmed);
            }
        }
        return List.copyOf(extensions);
    }

    private static void applyConfig(NativeBundleInfo bundleInfo) {
        try (Arena arena = Arena.ofConfined()) {
            setConfigOption(arena, "GDAL_DATA", bundleInfo.gdalData());
            setConfigOption(arena, "PROJ_LIB", bundleInfo.projData());
            setConfigOption(arena, "GDAL_DRIVER_PATH", bundleInfo.driverPath());
        }
    }

    private static void setConfigOption(Arena arena, String key, Path value) {
        if (value == null) {
            return;
        }

        MemorySegment keyString = arena.allocateFrom(key);
        MemorySegment valueString = arena.allocateFrom(value.toAbsolutePath().toString());
        GdalGenerated.CPLSetConfigOption(keyString, valueString);
        System.setProperty(key, value.toAbsolutePath().toString());
    }

    @FunctionalInterface
    private interface NativeFree {
        void free(MemorySegment segment);
    }

    private static void freeQuietly(MemorySegment segment, NativeFree free) {
        if (CStrings.isNull(segment)) {
            return;
        }
        try {
            free.free(segment);
        } catch (RuntimeException ignored) {
            // Keep cleanup best-effort and preserve root cause from the utility call.
        }
    }

    private static List<String> withInputArg(DatasetRef input, String... args) {
        Objects.requireNonNull(input, "input must not be null");
        ArrayList<String> commandLineArgs = new ArrayList<>();
        if (args != null) {
            commandLineArgs.addAll(List.of(args));
        }
        commandLineArgs.add("-i");
        commandLineArgs.add(input.toGdalIdentifier());
        return commandLineArgs;
    }

    private static List<String> withInputOutputArgs(DatasetRef input, DatasetRef output, String... args) {
        Objects.requireNonNull(output, "output must not be null");
        ArrayList<String> commandLineArgs = new ArrayList<>(withInputArg(input, args));
        commandLineArgs.add("-o");
        commandLineArgs.add(output.toGdalIdentifier());
        return commandLineArgs;
    }

    private static List<String> withInputOutputArgs(List<DatasetRef> inputs, DatasetRef output, String... args) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        Objects.requireNonNull(output, "output must not be null");
        ArrayList<String> commandLineArgs = new ArrayList<>();
        if (args != null) {
            commandLineArgs.addAll(List.of(args));
        }
        for (DatasetRef input : inputs) {
            commandLineArgs.add("-i");
            commandLineArgs.add(input.toGdalIdentifier());
        }
        commandLineArgs.add("-o");
        commandLineArgs.add(output.toGdalIdentifier());
        return commandLineArgs;
    }

    private static void closeDatasetQuietly(MemorySegment dataset) {
        if (CStrings.isNull(dataset)) {
            return;
        }
        try {
            GdalGenerated.GDALClose(dataset);
        } catch (RuntimeException ignored) {
            // Keep cleanup best-effort and preserve root cause from the utility call.
        }
    }

    private static void throwIfCallbackFailed(ProgressBridge.ProgressHandle progressHandle) {
        RuntimeException callbackFailure = progressHandle.callbackFailure();
        if (callbackFailure != null) {
            throw callbackFailure;
        }
    }
}
