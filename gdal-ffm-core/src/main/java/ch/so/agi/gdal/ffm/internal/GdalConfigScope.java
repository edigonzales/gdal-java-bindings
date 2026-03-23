package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.GdalConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class GdalConfigScope {
    private GdalConfigScope() {
    }

    public static ScopedConfigHandle applyScoped(GdalConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        GdalRuntime.initialize();
        if (config.isEmpty()) {
            return ScopedConfigHandle.NOOP;
        }

        LinkedHashMap<String, String> previousValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : config.options().entrySet()) {
            previousValues.put(entry.getKey(), GdalNative.getThreadLocalConfigOption(entry.getKey()));
            GdalNative.setThreadLocalConfigOption(entry.getKey(), entry.getValue());
        }
        return new ScopedConfigHandle(previousValues);
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
