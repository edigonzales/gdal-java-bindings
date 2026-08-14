package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.GdalConfig;
import ch.so.agi.gdal.ffm.ProgressCallback;
import ch.so.agi.gdal.ffm.generated.GdalGenerated;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.Objects;

final class GdalAlgorithmRunner {
    // GDALAlgorithmArgType::GAAT_STRING from gdalalgorithm.h.
    private static final int GAAT_STRING = 1;

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
            registry = GdalGenerated.GDALGetGlobalAlgorithmRegistry();
            if (CStrings.isNull(registry)) {
                throw GdalErrors.lastError("Failed to obtain GDAL algorithm registry");
            }

            MemorySegment algorithmPathArray = CArgv.toCStringArray(algorithmPath.toArray(String[]::new), arena);
            algorithm = GdalGenerated.GDALAlgorithmRegistryInstantiateAlgFromPath(registry, algorithmPathArray);
            if (CStrings.isNull(algorithm)) {
                throw GdalErrors.lastError("Failed to instantiate GDAL algorithm: " + String.join(" ", algorithmPath));
            }

            MemorySegment argv = CArgv.toCStringArray(args.toArray(String[]::new), arena);
            if (!GdalGenerated.GDALAlgorithmParseCommandLineArguments(algorithm, argv)) {
                throw GdalErrors.lastError(
                        "Failed to parse arguments for GDAL algorithm: " + String.join(" ", algorithmPath)
                );
            }

            if (!GdalGenerated.GDALAlgorithmRun(algorithm, progressHandle.callbackFn(), progressHandle.userData())) {
                throwIfCallbackFailed(progressHandle);
                throw GdalErrors.lastError("GDAL algorithm failed: " + String.join(" ", algorithmPath));
            }

            throwIfCallbackFailed(progressHandle);

            if (expectStringOutput) {
                stringOutput = readFirstStringOutput(algorithm);
            }

            if (!GdalGenerated.GDALAlgorithmFinalize(algorithm)) {
                throw GdalErrors.lastError("Failed to finalize GDAL algorithm: " + String.join(" ", algorithmPath));
            }

            return stringOutput;
        } finally {
            if (!CStrings.isNull(algorithm)) {
                GdalGenerated.GDALAlgorithmRelease(algorithm);
            }
            if (!CStrings.isNull(registry)) {
                GdalGenerated.GDALAlgorithmRegistryRelease(registry);
            }
        }
    }

    private static String readFirstStringOutput(MemorySegment algorithm) {
        MemorySegment actualAlgorithm = GdalGenerated.GDALAlgorithmGetActualAlgorithm(algorithm);
        if (CStrings.isNull(actualAlgorithm)) {
            actualAlgorithm = algorithm;
        }

        MemorySegment argNames = GdalGenerated.GDALAlgorithmGetArgNames(actualAlgorithm);
        if (CStrings.isNull(argNames)) {
            return "";
        }

        try {
            int argCount = GdalNative.CSLCount(argNames);
            MemorySegment namesArray = argNames.reinterpret((long) argCount * ValueLayout.ADDRESS.byteSize());
            for (int i = 0; i < argCount; i++) {
                MemorySegment argNamePtr = namesArray.getAtIndex(ValueLayout.ADDRESS, i);
                String argName = CStrings.fromCString(argNamePtr);
                MemorySegment arg = GdalGenerated.GDALAlgorithmGetArg(actualAlgorithm, argNamePtr);
                if (CStrings.isNull(arg)) {
                    continue;
                }
                try {
                    if (!GdalGenerated.GDALAlgorithmArgIsOutput(arg)) {
                        continue;
                    }
                    if (GdalGenerated.GDALAlgorithmArgGetType(arg) != GAAT_STRING) {
                        continue;
                    }
                    String value = CStrings.fromCString(GdalGenerated.GDALAlgorithmArgGetAsString(arg));
                    if (!value.isBlank()) {
                        return value;
                    }
                } finally {
                    GdalGenerated.GDALAlgorithmArgRelease(arg);
                }
            }
            return "";
        } finally {
            GdalGenerated.CSLDestroy(argNames);
        }
    }

    private static void throwIfCallbackFailed(ProgressBridge.ProgressHandle progressHandle) {
        RuntimeException callbackFailure = progressHandle.callbackFailure();
        if (callbackFailure != null) {
            throw callbackFailure;
        }
    }
}
