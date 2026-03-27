package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.GdalConfig;
import ch.so.agi.gdal.ffm.ProgressCallback;
import ch.so.agi.gdal.ffm.generated.GdalGenerated;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class GdalAlgorithmRunner {
    private static final int GAAT_STRING = 1;
    private static final int MAX_ARG_NAMES = 512;

    private GdalAlgorithmRunner() {
    }

    static void run(List<String> algorithmPath, GdalConfig config, ProgressCallback progress, List<String> args) {
        execute(algorithmPath, config, progress, args, false);
    }

    static String runForStringOutput(
            List<String> algorithmPath,
            GdalConfig config,
            ProgressCallback progress,
            List<String> args
    ) {
        return execute(algorithmPath, config, progress, args, true);
    }

    private static String execute(
            List<String> algorithmPath,
            GdalConfig config,
            ProgressCallback progress,
            List<String> args,
            boolean expectStringOutput
    ) {
        Objects.requireNonNull(algorithmPath, "algorithmPath must not be null");
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(args, "args must not be null");
        if (algorithmPath.isEmpty()) {
            throw new IllegalArgumentException("algorithmPath must not be empty");
        }

        MemorySegment registry = MemorySegment.NULL;
        MemorySegment algorithm = MemorySegment.NULL;
        String stringOutput = "";

        GdalGenerated.CPLErrorReset();
        try (GdalConfigScope.ScopedConfigHandle ignored = GdalConfigScope.applyScoped(config);
             Arena arena = Arena.ofConfined();
             ProgressBridge.ProgressHandle progressHandle = ProgressBridge.create(progress, arena)) {
            registry = GdalNative.GDALGetGlobalAlgorithmRegistry();
            if (CStrings.isNull(registry)) {
                throw GdalErrors.lastError("Failed to obtain GDAL algorithm registry");
            }

            MemorySegment algorithmPathArray = CArgv.toCStringArray(algorithmPath.toArray(String[]::new), arena);
            algorithm = GdalNative.GDALAlgorithmRegistryInstantiateAlgFromPath(registry, algorithmPathArray);
            if (CStrings.isNull(algorithm)) {
                throw GdalErrors.lastError("Failed to instantiate GDAL algorithm: " + String.join(" ", algorithmPath));
            }

            MemorySegment argv = CArgv.toCStringArray(args.toArray(String[]::new), arena);
            if (!GdalNative.GDALAlgorithmParseCommandLineArguments(algorithm, argv)) {
                throw GdalErrors.lastError(
                        "Failed to parse arguments for GDAL algorithm: " + String.join(" ", algorithmPath)
                );
            }

            if (!GdalNative.GDALAlgorithmRun(algorithm, progressHandle.callbackFn(), progressHandle.userData())) {
                throwIfCallbackFailed(progressHandle);
                throw GdalErrors.lastError("GDAL algorithm failed: " + String.join(" ", algorithmPath));
            }

            throwIfCallbackFailed(progressHandle);

            if (expectStringOutput) {
                stringOutput = readFirstStringOutput(algorithm);
            }

            if (!GdalNative.GDALAlgorithmFinalize(algorithm)) {
                throw GdalErrors.lastError("Failed to finalize GDAL algorithm: " + String.join(" ", algorithmPath));
            }

            return stringOutput;
        } finally {
            if (!CStrings.isNull(algorithm)) {
                GdalNative.GDALAlgorithmRelease(algorithm);
            }
            if (!CStrings.isNull(registry)) {
                GdalNative.GDALAlgorithmRegistryRelease(registry);
            }
        }
    }

    private static String readFirstStringOutput(MemorySegment algorithm) {
        MemorySegment actualAlgorithm = GdalNative.GDALAlgorithmGetActualAlgorithm(algorithm);
        if (CStrings.isNull(actualAlgorithm)) {
            actualAlgorithm = algorithm;
        }

        MemorySegment argNames = GdalNative.GDALAlgorithmGetArgNames(actualAlgorithm);
        if (CStrings.isNull(argNames)) {
            return "";
        }

        try {
            MemorySegment namesArray = argNames.reinterpret((long) MAX_ARG_NAMES * ValueLayout.ADDRESS.byteSize());
            for (int i = 0; i < MAX_ARG_NAMES; i++) {
                MemorySegment argNamePtr = namesArray.getAtIndex(ValueLayout.ADDRESS, i);
                if (CStrings.isNull(argNamePtr)) {
                    break;
                }

                String argName = CStrings.fromCString(argNamePtr);
                MemorySegment arg = GdalNative.GDALAlgorithmGetArg(actualAlgorithm, argNamePtr);
                if (CStrings.isNull(arg)) {
                    continue;
                }
                try {
                    if (!GdalNative.GDALAlgorithmArgIsOutput(arg)) {
                        continue;
                    }
                    if (GdalNative.GDALAlgorithmArgGetType(arg) != GAAT_STRING) {
                        continue;
                    }
                    String value = CStrings.fromCString(GdalNative.GDALAlgorithmArgGetAsString(arg));
                    if (!value.isBlank()) {
                        return value;
                    }
                } finally {
                    GdalNative.GDALAlgorithmArgRelease(arg);
                }
            }
            return "";
        } finally {
            GdalNative.CSLDestroy(argNames);
        }
    }

    private static void throwIfCallbackFailed(ProgressBridge.ProgressHandle progressHandle) {
        RuntimeException callbackFailure = progressHandle.callbackFailure();
        if (callbackFailure != null) {
            throw callbackFailure;
        }
    }
}
