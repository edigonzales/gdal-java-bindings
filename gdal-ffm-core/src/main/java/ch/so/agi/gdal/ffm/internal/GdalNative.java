package ch.so.agi.gdal.ffm.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class GdalNative {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SYMBOL_LOOKUP = SymbolLookup.loaderLookup().or(LINKER.defaultLookup());

    private static final MethodHandle GDAL_INFO_OPTIONS_NEW = downcall(
            "GDALInfoOptionsNew",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_INFO_OPTIONS_FREE = downcall(
            "GDALInfoOptionsFree",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_INFO = downcall(
            "GDALInfo",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_BUILD_VRT_OPTIONS_NEW = downcall(
            "GDALBuildVRTOptionsNew",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_BUILD_VRT_OPTIONS_FREE = downcall(
            "GDALBuildVRTOptionsFree",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_BUILD_VRT_OPTIONS_SET_PROGRESS = downcall(
            "GDALBuildVRTOptionsSetProgress",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_BUILD_VRT = downcall(
            "GDALBuildVRT",
            FunctionDescriptor.of(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            )
    );
    private static final MethodHandle GDAL_RASTERIZE_OPTIONS_NEW = downcall(
            "GDALRasterizeOptionsNew",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_RASTERIZE_OPTIONS_FREE = downcall(
            "GDALRasterizeOptionsFree",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_RASTERIZE_OPTIONS_SET_PROGRESS = downcall(
            "GDALRasterizeOptionsSetProgress",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_RASTERIZE = downcall(
            "GDALRasterize",
            FunctionDescriptor.of(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            )
    );
    private static final MethodHandle CPL_SET_THREAD_LOCAL_CONFIG_OPTION = downcall(
            "CPLSetThreadLocalConfigOption",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle CPL_GET_THREAD_LOCAL_CONFIG_OPTION = downcall(
            "CPLGetThreadLocalConfigOption",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );

    private GdalNative() {
    }

    static MemorySegment GDALInfoOptionsNew(MemorySegment argv) {
        return invokeAddress(GDAL_INFO_OPTIONS_NEW, argv, MemorySegment.NULL);
    }

    static void GDALInfoOptionsFree(MemorySegment options) {
        invokeVoid(GDAL_INFO_OPTIONS_FREE, options);
    }

    static MemorySegment GDALInfo(MemorySegment dataset, MemorySegment options) {
        return invokeAddress(GDAL_INFO, dataset, options);
    }

    static MemorySegment GDALBuildVRTOptionsNew(MemorySegment argv) {
        return invokeAddress(GDAL_BUILD_VRT_OPTIONS_NEW, argv, MemorySegment.NULL);
    }

    static void GDALBuildVRTOptionsFree(MemorySegment options) {
        invokeVoid(GDAL_BUILD_VRT_OPTIONS_FREE, options);
    }

    static void GDALBuildVRTOptionsSetProgress(
            MemorySegment options,
            MemorySegment callback,
            MemorySegment userData
    ) {
        invokeVoid(GDAL_BUILD_VRT_OPTIONS_SET_PROGRESS, options, callback, userData);
    }

    static MemorySegment GDALBuildVRT(
            MemorySegment destination,
            int sourceCount,
            MemorySegment sourceNames,
            MemorySegment options,
            MemorySegment usageError
    ) {
        return invokeAddress(
                GDAL_BUILD_VRT,
                destination,
                sourceCount,
                MemorySegment.NULL,
                sourceNames,
                options,
                usageError
        );
    }

    static MemorySegment GDALRasterizeOptionsNew(MemorySegment argv) {
        return invokeAddress(GDAL_RASTERIZE_OPTIONS_NEW, argv, MemorySegment.NULL);
    }

    static void GDALRasterizeOptionsFree(MemorySegment options) {
        invokeVoid(GDAL_RASTERIZE_OPTIONS_FREE, options);
    }

    static void GDALRasterizeOptionsSetProgress(
            MemorySegment options,
            MemorySegment callback,
            MemorySegment userData
    ) {
        invokeVoid(GDAL_RASTERIZE_OPTIONS_SET_PROGRESS, options, callback, userData);
    }

    static MemorySegment GDALRasterize(
            MemorySegment destination,
            MemorySegment sourceDataset,
            MemorySegment options,
            MemorySegment usageError
    ) {
        return invokeAddress(
                GDAL_RASTERIZE,
                destination,
                MemorySegment.NULL,
                sourceDataset,
                options,
                usageError
        );
    }

    static void setThreadLocalConfigOption(String key, String value) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment keyString = arena.allocateFrom(key);
            MemorySegment valueString = value == null ? MemorySegment.NULL : arena.allocateFrom(value);
            invokeVoid(CPL_SET_THREAD_LOCAL_CONFIG_OPTION, keyString, valueString);
        }
    }

    static String getThreadLocalConfigOption(String key) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment keyString = arena.allocateFrom(key);
            MemorySegment result = invokeAddress(CPL_GET_THREAD_LOCAL_CONFIG_OPTION, keyString, MemorySegment.NULL);
            return CStrings.isNull(result) ? null : CStrings.fromCString(result);
        }
    }

    private static MethodHandle downcall(String symbolName, FunctionDescriptor descriptor) {
        MemorySegment symbol = SYMBOL_LOOKUP.find(symbolName)
                .orElseThrow(() -> new IllegalStateException("Required GDAL symbol not found: " + symbolName));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static MemorySegment invokeAddress(MethodHandle handle, Object... args) {
        try {
            return (MemorySegment) handle.invokeWithArguments(args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Native GDAL invocation failed", e);
        }
    }

    private static void invokeVoid(MethodHandle handle, Object... args) {
        try {
            handle.invokeWithArguments(args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Native GDAL invocation failed", e);
        }
    }
}
