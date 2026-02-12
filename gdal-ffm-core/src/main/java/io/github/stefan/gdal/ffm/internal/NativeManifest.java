package io.github.stefan.gdal.ffm.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record NativeManifest(
        String bundleVersion,
        String entryLibrary,
        List<String> preloadLibraries,
        String gdalDataPath,
        String projDataPath,
        String driverPath
) {
    static NativeManifest parse(String json) {
        String bundleVersion = stringField(json, "bundleVersion").orElse("unknown");
        String entryLibrary = stringField(json, "entryLibrary")
                .orElseThrow(() -> new IllegalStateException("manifest.json misses required field 'entryLibrary'"));
        List<String> preload = arrayField(json, "preloadLibraries");
        String gdalDataPath = stringField(json, "gdalDataPath").orElse(null);
        String projDataPath = stringField(json, "projDataPath").orElse(null);
        String driverPath = stringField(json, "driverPath").orElse(null);
        return new NativeManifest(bundleVersion, entryLibrary, preload, gdalDataPath, projDataPath, driverPath);
    }

    private static Optional<String> stringField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    private static List<String> arrayField(String json, String field) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(field) + "\"\\s*:\\s*\\[(.*?)]",
                Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return List.of();
        }

        String body = matcher.group(1);
        Pattern itemPattern = Pattern.compile("\"([^\"]+)\"");
        Matcher itemMatcher = itemPattern.matcher(body);
        List<String> values = new ArrayList<>();
        while (itemMatcher.find()) {
            values.add(itemMatcher.group(1));
        }
        return List.copyOf(values);
    }
}
