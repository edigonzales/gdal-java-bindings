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

    private static final MethodHandle CPL_SET_THREAD_LOCAL_CONFIG_OPTION = downcall(
            "CPLSetThreadLocalConfigOption",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle CPL_GET_THREAD_LOCAL_CONFIG_OPTION = downcall(
            "CPLGetThreadLocalConfigOption",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle CSL_DESTROY = downcall(
            "CSLDestroy",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_GET_GLOBAL_ALGORITHM_REGISTRY = downcall(
            "GDALGetGlobalAlgorithmRegistry",
            FunctionDescriptor.of(ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_REGISTRY_RELEASE = downcall(
            "GDALAlgorithmRegistryRelease",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_REGISTRY_INSTANTIATE_ALG_FROM_PATH = downcall(
            "GDALAlgorithmRegistryInstantiateAlgFromPath",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_RELEASE = downcall(
            "GDALAlgorithmRelease",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_PARSE_COMMAND_LINE_ARGUMENTS = downcall(
            "GDALAlgorithmParseCommandLineArguments",
            FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_GET_ACTUAL_ALGORITHM = downcall(
            "GDALAlgorithmGetActualAlgorithm",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_RUN = downcall(
            "GDALAlgorithmRun",
            FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_FINALIZE = downcall(
            "GDALAlgorithmFinalize",
            FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_GET_ARG_NAMES = downcall(
            "GDALAlgorithmGetArgNames",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_GET_ARG = downcall(
            "GDALAlgorithmGetArg",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_ARG_RELEASE = downcall(
            "GDALAlgorithmArgRelease",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_ARG_GET_TYPE = downcall(
            "GDALAlgorithmArgGetType",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_ARG_IS_OUTPUT = downcall(
            "GDALAlgorithmArgIsOutput",
            FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS)
    );
    private static final MethodHandle GDAL_ALGORITHM_ARG_GET_AS_STRING = downcall(
            "GDALAlgorithmArgGetAsString",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );

    private GdalNative() {
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

    static void CSLDestroy(MemorySegment strings) {
        invokeVoid(CSL_DESTROY, strings);
    }

    static MemorySegment GDALGetGlobalAlgorithmRegistry() {
        return invokeAddress(GDAL_GET_GLOBAL_ALGORITHM_REGISTRY);
    }

    static void GDALAlgorithmRegistryRelease(MemorySegment registry) {
        invokeVoid(GDAL_ALGORITHM_REGISTRY_RELEASE, registry);
    }

    static MemorySegment GDALAlgorithmRegistryInstantiateAlgFromPath(MemorySegment registry, MemorySegment algPath) {
        return invokeAddress(GDAL_ALGORITHM_REGISTRY_INSTANTIATE_ALG_FROM_PATH, registry, algPath);
    }

    static void GDALAlgorithmRelease(MemorySegment algorithm) {
        invokeVoid(GDAL_ALGORITHM_RELEASE, algorithm);
    }

    static boolean GDALAlgorithmParseCommandLineArguments(MemorySegment algorithm, MemorySegment argv) {
        return invokeBoolean(GDAL_ALGORITHM_PARSE_COMMAND_LINE_ARGUMENTS, algorithm, argv);
    }

    static MemorySegment GDALAlgorithmGetActualAlgorithm(MemorySegment algorithm) {
        return invokeAddress(GDAL_ALGORITHM_GET_ACTUAL_ALGORITHM, algorithm);
    }

    static boolean GDALAlgorithmRun(MemorySegment algorithm, MemorySegment callback, MemorySegment userData) {
        return invokeBoolean(GDAL_ALGORITHM_RUN, algorithm, callback, userData);
    }

    static boolean GDALAlgorithmFinalize(MemorySegment algorithm) {
        return invokeBoolean(GDAL_ALGORITHM_FINALIZE, algorithm);
    }

    static MemorySegment GDALAlgorithmGetArgNames(MemorySegment algorithm) {
        return invokeAddress(GDAL_ALGORITHM_GET_ARG_NAMES, algorithm);
    }

    static MemorySegment GDALAlgorithmGetArg(MemorySegment algorithm, MemorySegment argName) {
        return invokeAddress(GDAL_ALGORITHM_GET_ARG, algorithm, argName);
    }

    static void GDALAlgorithmArgRelease(MemorySegment arg) {
        invokeVoid(GDAL_ALGORITHM_ARG_RELEASE, arg);
    }

    static int GDALAlgorithmArgGetType(MemorySegment arg) {
        return invokeInt(GDAL_ALGORITHM_ARG_GET_TYPE, arg);
    }

    static boolean GDALAlgorithmArgIsOutput(MemorySegment arg) {
        return invokeBoolean(GDAL_ALGORITHM_ARG_IS_OUTPUT, arg);
    }

    static MemorySegment GDALAlgorithmArgGetAsString(MemorySegment arg) {
        return invokeAddress(GDAL_ALGORITHM_ARG_GET_AS_STRING, arg);
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

    private static boolean invokeBoolean(MethodHandle handle, Object... args) {
        try {
            return (boolean) handle.invokeWithArguments(args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Native GDAL invocation failed", e);
        }
    }

    private static int invokeInt(MethodHandle handle, Object... args) {
        try {
            return (int) handle.invokeWithArguments(args);
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
