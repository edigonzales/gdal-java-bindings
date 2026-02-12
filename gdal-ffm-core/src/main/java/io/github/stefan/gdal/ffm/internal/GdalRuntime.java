package io.github.stefan.gdal.ffm.internal;

import io.github.stefan.gdal.ffm.ProgressCallback;
import io.github.stefan.gdal.ffm.generated.GdalGenerated;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GdalRuntime {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Object INIT_LOCK = new Object();

    private static final int GDAL_OF_RASTER = 0x02;
    private static final int GDAL_OF_VECTOR = 0x04;
    private static final int GDAL_OF_VERBOSE_ERROR = 0x40;

    private GdalRuntime() {
    }

    public static void vectorTranslate(Path dest, Path src, ProgressCallback progress, String... args) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        ensureInitialized();

        MemorySegment options = MemorySegment.NULL;
        MemorySegment sourceDataset = MemorySegment.NULL;
        MemorySegment resultDataset = MemorySegment.NULL;

        GdalGenerated.CPLErrorReset();
        try (Arena arena = Arena.ofConfined(); ProgressBridge.ProgressHandle progressHandle = ProgressBridge.create(progress, arena)) {
            MemorySegment argv = CArgv.toCStringArray(args, arena);
            options = GdalGenerated.GDALVectorTranslateOptionsNew(argv, MemorySegment.NULL);
            if (CStrings.isNull(options)) {
                throw GdalErrors.lastError("Failed to create GDALVectorTranslate options");
            }

            if (!CStrings.isNull(progressHandle.callbackFn())) {
                GdalGenerated.GDALVectorTranslateOptionsSetProgress(options, progressHandle.callbackFn(), progressHandle.userData());
            }

            sourceDataset = openDataset(src, GDAL_OF_VECTOR | GDAL_OF_VERBOSE_ERROR, arena);

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

    public static void warp(Path dest, Path src, ProgressCallback progress, String... args) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        ensureInitialized();

        MemorySegment options = MemorySegment.NULL;
        MemorySegment sourceDataset = MemorySegment.NULL;
        MemorySegment resultDataset = MemorySegment.NULL;

        GdalGenerated.CPLErrorReset();
        try (Arena arena = Arena.ofConfined(); ProgressBridge.ProgressHandle progressHandle = ProgressBridge.create(progress, arena)) {
            MemorySegment argv = CArgv.toCStringArray(args, arena);
            options = GdalGenerated.GDALWarpAppOptionsNew(argv, MemorySegment.NULL);
            if (CStrings.isNull(options)) {
                throw GdalErrors.lastError("Failed to create GDALWarpAppOptions");
            }

            if (!CStrings.isNull(progressHandle.callbackFn())) {
                GdalGenerated.GDALWarpAppOptionsSetProgress(options, progressHandle.callbackFn(), progressHandle.userData());
            }

            sourceDataset = openDataset(src, GDAL_OF_RASTER | GDAL_OF_VERBOSE_ERROR, arena);

            MemorySegment sources = arena.allocate(ValueLayout.ADDRESS);
            sources.set(ValueLayout.ADDRESS, 0, sourceDataset);

            MemorySegment usageError = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment destination = arena.allocateFrom(dest.toAbsolutePath().toString());

            resultDataset = GdalGenerated.GDALWarp(
                    destination,
                    MemorySegment.NULL,
                    1,
                    sources,
                    options,
                    usageError
            );

            throwIfCallbackFailed(progressHandle);

            if (usageError.get(ValueLayout.JAVA_INT, 0) != 0 || CStrings.isNull(resultDataset)) {
                throw GdalErrors.lastError("GDALWarp failed");
            }
        } finally {
            freeQuietly(options, GdalGenerated::GDALWarpAppOptionsFree);
            closeDatasetQuietly(resultDataset);
            closeDatasetQuietly(sourceDataset);
        }
    }

    public static void translate(Path dest, Path src, ProgressCallback progress, String... args) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        ensureInitialized();

        MemorySegment options = MemorySegment.NULL;
        MemorySegment sourceDataset = MemorySegment.NULL;
        MemorySegment resultDataset = MemorySegment.NULL;

        GdalGenerated.CPLErrorReset();
        try (Arena arena = Arena.ofConfined(); ProgressBridge.ProgressHandle progressHandle = ProgressBridge.create(progress, arena)) {
            MemorySegment argv = CArgv.toCStringArray(args, arena);
            options = GdalGenerated.GDALTranslateOptionsNew(argv, MemorySegment.NULL);
            if (CStrings.isNull(options)) {
                throw GdalErrors.lastError("Failed to create GDALTranslateOptions");
            }

            if (!CStrings.isNull(progressHandle.callbackFn())) {
                GdalGenerated.GDALTranslateOptionsSetProgress(options, progressHandle.callbackFn(), progressHandle.userData());
            }

            sourceDataset = openDataset(src, GDAL_OF_RASTER | GDAL_OF_VERBOSE_ERROR, arena);

            MemorySegment usageError = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment destination = arena.allocateFrom(dest.toAbsolutePath().toString());

            resultDataset = GdalGenerated.GDALTranslate(
                    destination,
                    sourceDataset,
                    options,
                    usageError
            );

            throwIfCallbackFailed(progressHandle);

            if (usageError.get(ValueLayout.JAVA_INT, 0) != 0 || CStrings.isNull(resultDataset)) {
                throw GdalErrors.lastError("GDALTranslate failed");
            }
        } finally {
            freeQuietly(options, GdalGenerated::GDALTranslateOptionsFree);
            closeDatasetQuietly(resultDataset);
            closeDatasetQuietly(sourceDataset);
        }
    }

    private static void ensureInitialized() {
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

    private static MemorySegment openDataset(Path src, int flags, Arena arena) {
        MemorySegment sourcePath = arena.allocateFrom(src.toAbsolutePath().toString());
        MemorySegment dataset = GdalGenerated.GDALOpenEx(
                sourcePath,
                flags,
                MemorySegment.NULL,
                MemorySegment.NULL,
                MemorySegment.NULL
        );
        if (CStrings.isNull(dataset)) {
            throw GdalErrors.lastError("Failed to open source dataset: " + src);
        }
        return dataset;
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
}
