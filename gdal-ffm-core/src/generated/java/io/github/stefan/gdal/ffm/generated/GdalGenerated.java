package io.github.stefan.gdal.ffm.generated;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Generated-style low-level FFM bridge for the symbol subset used by the high-level API.
 *
 * <p>This file is intentionally checked in and can be regenerated via tools/jextract/regenerate.sh.</p>
 */
public final class GdalGenerated {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    private static final FunctionDescriptor PROGRESS_FUNCTION_DESCRIPTOR = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_DOUBLE,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
    );

    private static final MethodHandle MH_GDAL_ALL_REGISTER = downcall(
            "GDALAllRegister",
            FunctionDescriptor.ofVoid()
    );
    private static final MethodHandle MH_GDAL_OPEN_EX = downcall(
            "GDALOpenEx",
            FunctionDescriptor.of(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            )
    );
    private static final MethodHandle MH_GDAL_CLOSE = downcall(
            "GDALClose",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
    );
    private static final MethodHandle MH_GDAL_RELEASE_DATASET = downcall(
            "GDALReleaseDataset",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
    );
    private static final MethodHandle MH_CPL_ERROR_RESET = downcall(
            "CPLErrorReset",
            FunctionDescriptor.ofVoid()
    );
    private static final MethodHandle MH_CPL_GET_LAST_ERROR_TYPE = downcall(
            "CPLGetLastErrorType",
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
    );
    private static final MethodHandle MH_CPL_GET_LAST_ERROR_NO = downcall(
            "CPLGetLastErrorNo",
            FunctionDescriptor.of(ValueLayout.JAVA_INT)
    );
    private static final MethodHandle MH_CPL_GET_LAST_ERROR_MSG = downcall(
            "CPLGetLastErrorMsg",
            FunctionDescriptor.of(ValueLayout.ADDRESS)
    );
    private static final MethodHandle MH_CPL_FREE = downcallFirstAvailable(
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
            "CPLFree",
            "VSIFree"
    );
    private static final MethodHandle MH_CPL_SET_CONFIG_OPTION = downcall(
            "CPLSetConfigOption",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );

    private static final MethodHandle MH_VECTOR_TRANSLATE_OPTIONS_NEW = downcall(
            "GDALVectorTranslateOptionsNew",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle MH_VECTOR_TRANSLATE_OPTIONS_FREE = downcall(
            "GDALVectorTranslateOptionsFree",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );
    private static final MethodHandle MH_VECTOR_TRANSLATE_OPTIONS_SET_PROGRESS = downcall(
            "GDALVectorTranslateOptionsSetProgress",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle MH_VECTOR_TRANSLATE = downcall(
            "GDALVectorTranslate",
            FunctionDescriptor.of(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            )
    );

    private static final MethodHandle MH_WARP_APP_OPTIONS_NEW = downcall(
            "GDALWarpAppOptionsNew",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle MH_WARP_APP_OPTIONS_FREE = downcall(
            "GDALWarpAppOptionsFree",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );
    private static final MethodHandle MH_WARP_APP_OPTIONS_SET_PROGRESS = downcall(
            "GDALWarpAppOptionsSetProgress",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle MH_GDAL_WARP = downcall(
            "GDALWarp",
            FunctionDescriptor.of(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            )
    );

    private static final MethodHandle MH_TRANSLATE_OPTIONS_NEW = downcall(
            "GDALTranslateOptionsNew",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle MH_TRANSLATE_OPTIONS_FREE = downcall(
            "GDALTranslateOptionsFree",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );
    private static final MethodHandle MH_TRANSLATE_OPTIONS_SET_PROGRESS = downcall(
            "GDALTranslateOptionsSetProgress",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle MH_TRANSLATE = downcall(
            "GDALTranslate",
            FunctionDescriptor.of(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            )
    );

    private GdalGenerated() {
    }

    public static void GDALAllRegister() {
        invokeVoid(MH_GDAL_ALL_REGISTER);
    }

    public static MemorySegment GDALOpenEx(
            MemorySegment filename,
            int openFlags,
            MemorySegment allowedDrivers,
            MemorySegment openOptions,
            MemorySegment siblingFiles
    ) {
        return (MemorySegment) invoke(
                MH_GDAL_OPEN_EX,
                filename,
                openFlags,
                allowedDrivers,
                openOptions,
                siblingFiles
        );
    }

    public static int GDALClose(MemorySegment dataset) {
        return (int) invoke(MH_GDAL_CLOSE, dataset);
    }

    public static int GDALReleaseDataset(MemorySegment dataset) {
        return (int) invoke(MH_GDAL_RELEASE_DATASET, dataset);
    }

    public static void CPLErrorReset() {
        invokeVoid(MH_CPL_ERROR_RESET);
    }

    public static int CPLGetLastErrorType() {
        return (int) invoke(MH_CPL_GET_LAST_ERROR_TYPE);
    }

    public static int CPLGetLastErrorNo() {
        return (int) invoke(MH_CPL_GET_LAST_ERROR_NO);
    }

    public static MemorySegment CPLGetLastErrorMsg() {
        return (MemorySegment) invoke(MH_CPL_GET_LAST_ERROR_MSG);
    }

    public static void CPLFree(MemorySegment pointer) {
        invokeVoid(MH_CPL_FREE, pointer);
    }

    public static void CPLSetConfigOption(MemorySegment key, MemorySegment value) {
        invokeVoid(MH_CPL_SET_CONFIG_OPTION, key, value);
    }

    public static MemorySegment GDALVectorTranslateOptionsNew(MemorySegment argv, MemorySegment optionsForBinary) {
        return (MemorySegment) invoke(MH_VECTOR_TRANSLATE_OPTIONS_NEW, argv, optionsForBinary);
    }

    public static void GDALVectorTranslateOptionsFree(MemorySegment options) {
        invokeVoid(MH_VECTOR_TRANSLATE_OPTIONS_FREE, options);
    }

    public static void GDALVectorTranslateOptionsSetProgress(
            MemorySegment options,
            MemorySegment progressFn,
            MemorySegment userData
    ) {
        invokeVoid(MH_VECTOR_TRANSLATE_OPTIONS_SET_PROGRESS, options, progressFn, userData);
    }

    public static MemorySegment GDALVectorTranslate(
            MemorySegment destination,
            MemorySegment destinationDataset,
            int sourceCount,
            MemorySegment sourceDatasetArray,
            MemorySegment options,
            MemorySegment usageError
    ) {
        return (MemorySegment) invoke(
                MH_VECTOR_TRANSLATE,
                destination,
                destinationDataset,
                sourceCount,
                sourceDatasetArray,
                options,
                usageError
        );
    }

    public static MemorySegment GDALWarpAppOptionsNew(MemorySegment argv, MemorySegment optionsForBinary) {
        return (MemorySegment) invoke(MH_WARP_APP_OPTIONS_NEW, argv, optionsForBinary);
    }

    public static void GDALWarpAppOptionsFree(MemorySegment options) {
        invokeVoid(MH_WARP_APP_OPTIONS_FREE, options);
    }

    public static void GDALWarpAppOptionsSetProgress(
            MemorySegment options,
            MemorySegment progressFn,
            MemorySegment userData
    ) {
        invokeVoid(MH_WARP_APP_OPTIONS_SET_PROGRESS, options, progressFn, userData);
    }

    public static MemorySegment GDALWarp(
            MemorySegment destination,
            MemorySegment destinationDataset,
            int sourceCount,
            MemorySegment sourceDatasetArray,
            MemorySegment options,
            MemorySegment usageError
    ) {
        return (MemorySegment) invoke(
                MH_GDAL_WARP,
                destination,
                destinationDataset,
                sourceCount,
                sourceDatasetArray,
                options,
                usageError
        );
    }

    public static MemorySegment GDALTranslateOptionsNew(MemorySegment argv, MemorySegment optionsForBinary) {
        return (MemorySegment) invoke(MH_TRANSLATE_OPTIONS_NEW, argv, optionsForBinary);
    }

    public static void GDALTranslateOptionsFree(MemorySegment options) {
        invokeVoid(MH_TRANSLATE_OPTIONS_FREE, options);
    }

    public static void GDALTranslateOptionsSetProgress(
            MemorySegment options,
            MemorySegment progressFn,
            MemorySegment userData
    ) {
        invokeVoid(MH_TRANSLATE_OPTIONS_SET_PROGRESS, options, progressFn, userData);
    }

    public static MemorySegment GDALTranslate(
            MemorySegment destination,
            MemorySegment sourceDataset,
            MemorySegment options,
            MemorySegment usageError
    ) {
        return (MemorySegment) invoke(MH_TRANSLATE, destination, sourceDataset, options, usageError);
    }

    public static MemorySegment upcallProgress(MethodHandle target, Arena arena) {
        return LINKER.upcallStub(target, PROGRESS_FUNCTION_DESCRIPTOR, arena);
    }

    private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
        MemorySegment address = LOOKUP.find(symbol)
                .orElseThrow(() -> new IllegalStateException("Required GDAL symbol not found: " + symbol));
        return LINKER.downcallHandle(address, descriptor);
    }

    private static MethodHandle downcallFirstAvailable(FunctionDescriptor descriptor, String... symbols) {
        for (String symbol : symbols) {
            MemorySegment address = LOOKUP.find(symbol).orElse(null);
            if (address != null) {
                return LINKER.downcallHandle(address, descriptor);
            }
        }
        throw new IllegalStateException("Required GDAL symbol not found: " + String.join(" or ", symbols));
    }

    private static Object invoke(MethodHandle methodHandle, Object... args) {
        try {
            return methodHandle.invokeWithArguments(args);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to invoke native symbol", throwable);
        }
    }

    private static void invokeVoid(MethodHandle methodHandle, Object... args) {
        invoke(methodHandle, args);
    }
}
