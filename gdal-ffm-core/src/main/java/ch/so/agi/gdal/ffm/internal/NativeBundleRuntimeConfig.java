package ch.so.agi.gdal.ffm.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

final class NativeBundleRuntimeConfig {
    static final String GDAL_DATA = "GDAL_DATA";
    static final String PROJ_LIB = "PROJ_LIB";
    static final String GDAL_DRIVER_PATH = "GDAL_DRIVER_PATH";
    static final String CURL_CA_BUNDLE = "CURL_CA_BUNDLE";
    static final String SSL_CERT_FILE = "SSL_CERT_FILE";

    private NativeBundleRuntimeConfig() {
    }

    static Map<String, Path> configOptions(NativeBundleInfo bundleInfo) {
        return configOptions(bundleInfo, System.getenv(), System.getProperties());
    }

    static Map<String, Path> configOptions(
            NativeBundleInfo bundleInfo,
            Map<String, String> environment,
            Properties systemProperties
    ) {
        Objects.requireNonNull(bundleInfo, "bundleInfo must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Objects.requireNonNull(systemProperties, "systemProperties must not be null");

        LinkedHashMap<String, Path> options = new LinkedHashMap<>();
        putIfPresent(options, GDAL_DATA, bundleInfo.gdalData());
        putIfPresent(options, PROJ_LIB, bundleInfo.projData());
        putIfPresent(options, GDAL_DRIVER_PATH, bundleInfo.driverPath());

        Path bundledCa = bundledCaBundle(bundleInfo, environment, systemProperties);
        if (bundledCa != null) {
            options.put(CURL_CA_BUNDLE, bundledCa);
            options.put(SSL_CERT_FILE, bundledCa);
        }

        return Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    static Path bundledCaBundle(
            NativeBundleInfo bundleInfo,
            Map<String, String> environment,
            Properties systemProperties
    ) {
        Objects.requireNonNull(bundleInfo, "bundleInfo must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Objects.requireNonNull(systemProperties, "systemProperties must not be null");

        if (!isUnixClassifier(bundleInfo.classifier()) || hasUserDefinedCaOption(environment, systemProperties)) {
            return null;
        }

        Path caBundle = bundleInfo.caBundle();
        if (caBundle == null || !Files.isRegularFile(caBundle)) {
            return null;
        }
        return caBundle.toAbsolutePath();
    }

    private static boolean isUnixClassifier(String classifier) {
        return classifier.startsWith("linux-") || classifier.startsWith("osx-");
    }

    private static boolean hasUserDefinedCaOption(Map<String, String> environment, Properties systemProperties) {
        return hasValue(environment.get(CURL_CA_BUNDLE))
                || hasValue(environment.get(SSL_CERT_FILE))
                || hasValue(systemProperties.getProperty(CURL_CA_BUNDLE))
                || hasValue(systemProperties.getProperty(SSL_CERT_FILE));
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private static void putIfPresent(Map<String, Path> options, String key, Path value) {
        if (value != null) {
            options.put(key, value.toAbsolutePath());
        }
    }
}
