package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.GdalConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class GdalConfigScope {
    private GdalConfigScope() {
    }

    public static ScopedConfigHandle applyScoped(GdalConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.initialize();
        Map<String, String> effectiveOptions =
                effectiveConfigOptions(config, NativeLoader.load(), System.getenv(), System.getProperties());
        if (effectiveOptions.isEmpty()) {
            return ScopedConfigHandle.NOOP;
        }

        LinkedHashMap<String, String> previousValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : effectiveOptions.entrySet()) {
            previousValues.put(entry.getKey(), GdalNative.getThreadLocalConfigOption(entry.getKey()));
            GdalNative.setThreadLocalConfigOption(entry.getKey(), entry.getValue());
        }
        return new ScopedConfigHandle(previousValues);
    }

    static Map<String, String> effectiveConfigOptions(
            GdalConfig config,
            NativeBundleInfo bundleInfo,
            Map<String, String> environment,
            Properties systemProperties
    ) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(bundleInfo, "bundleInfo must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Objects.requireNonNull(systemProperties, "systemProperties must not be null");

        LinkedHashMap<String, String> effectiveOptions = new LinkedHashMap<>();
        effectiveOptions.putAll(NativeBundleRuntimeConfig.scopedConfigOptions(bundleInfo, environment, systemProperties));
        effectiveOptions.putAll(config.options());
        return Collections.unmodifiableMap(new LinkedHashMap<>(effectiveOptions));
    }

    public static final class ScopedConfigHandle implements AutoCloseable {
        private static final ScopedConfigHandle NOOP = new ScopedConfigHandle(Map.of());

        private final Map<String, String> previousValues;
        private boolean closed;

        private ScopedConfigHandle(Map<String, String> previousValues) {
            this.previousValues = previousValues;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            for (Map.Entry<String, String> entry : previousValues.entrySet()) {
                GdalNative.setThreadLocalConfigOption(entry.getKey(), entry.getValue());
            }
        }
    }
}
