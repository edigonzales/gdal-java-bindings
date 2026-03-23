package ch.so.agi.gdal.ffm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record GdalConfig(Map<String, String> options) {
    private static final GdalConfig EMPTY = new GdalConfig(Map.of());

    public GdalConfig {
        Objects.requireNonNull(options, "options must not be null");

        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            String key = normalize(entry.getKey(), "option key");
            String value = Objects.requireNonNull(entry.getValue(), "option value must not be null");
            normalized.put(key, value);
        }
        options = Map.copyOf(normalized);
    }

    public static GdalConfig empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return options.isEmpty();
    }

    public GdalConfig withConfigOption(String key, String value) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(options);
        merged.put(normalize(key, "option key"), Objects.requireNonNull(value, "value must not be null"));
        return new GdalConfig(merged);
    }

    public GdalConfig withConfig(Map<String, String> additionalOptions) {
        Objects.requireNonNull(additionalOptions, "additionalOptions must not be null");
        LinkedHashMap<String, String> merged = new LinkedHashMap<>(options);
        for (Map.Entry<String, String> entry : additionalOptions.entrySet()) {
            merged.put(normalize(entry.getKey(), "option key"), Objects.requireNonNull(entry.getValue(), "value must not be null"));
        }
        return new GdalConfig(merged);
    }

    private static String normalize(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
