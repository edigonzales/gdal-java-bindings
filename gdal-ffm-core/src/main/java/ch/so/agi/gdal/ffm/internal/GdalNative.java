package ch.so.agi.gdal.ffm.internal;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Temporary compatibility bridge for GDAL C functions that are not yet present in the checked-in
 * jextract output. Keep this class deliberately small; normal GDAL calls belong in GdalGenerated.
 */
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
    private static final MethodHandle CSL_COUNT = downcall(
            "CSLCount",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
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

    static int CSLCount(MemorySegment strings) {
        return invokeInt(CSL_COUNT, strings);
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
