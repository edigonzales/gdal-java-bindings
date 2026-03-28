package ch.so.agi.gdal.ffm.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class NativeBundleRuntimeConfigTest {
    @Test
    void includesBundledCaDefaultsForUnixBundleWhenNoOverrideExists() throws Exception {
        NativeBundleInfo bundleInfo = createBundleInfo("osx-aarch64");

        Map<String, Path> options = NativeBundleRuntimeConfig.configOptions(bundleInfo, Map.of(), new Properties());

        assertEquals(bundleInfo.gdalData().toAbsolutePath(), options.get(NativeBundleRuntimeConfig.GDAL_DATA));
        assertEquals(bundleInfo.projData().toAbsolutePath(), options.get(NativeBundleRuntimeConfig.PROJ_LIB));
        assertEquals(
                bundleInfo.driverPath().toAbsolutePath(),
                options.get(NativeBundleRuntimeConfig.GDAL_DRIVER_PATH)
        );
        assertEquals(
                bundleInfo.caBundle().toAbsolutePath(),
                options.get(NativeBundleRuntimeConfig.CURL_CA_BUNDLE)
        );
        assertEquals(
                bundleInfo.caBundle().toAbsolutePath(),
                options.get(NativeBundleRuntimeConfig.SSL_CERT_FILE)
        );
    }

    @Test
    void doesNotIncludeBundledCaDefaultsForNonUnixBundle() throws Exception {
        NativeBundleInfo bundleInfo = createBundleInfo("windows-x86_64");

        Map<String, Path> options = NativeBundleRuntimeConfig.configOptions(bundleInfo, Map.of(), new Properties());

        assertFalse(options.containsKey(NativeBundleRuntimeConfig.CURL_CA_BUNDLE));
        assertFalse(options.containsKey(NativeBundleRuntimeConfig.SSL_CERT_FILE));
    }

    @Test
    void skipsBundledCaDefaultsWhenEnvironmentDefinesCurlCaBundle() throws Exception {
        NativeBundleInfo bundleInfo = createBundleInfo("linux-x86_64");

        Map<String, Path> options = NativeBundleRuntimeConfig.configOptions(
                bundleInfo,
                Map.of(NativeBundleRuntimeConfig.CURL_CA_BUNDLE, "/custom/ca.pem"),
                new Properties()
        );

        assertFalse(options.containsKey(NativeBundleRuntimeConfig.CURL_CA_BUNDLE));
        assertFalse(options.containsKey(NativeBundleRuntimeConfig.SSL_CERT_FILE));
        assertTrue(options.containsKey(NativeBundleRuntimeConfig.GDAL_DATA));
    }

    @Test
    void skipsBundledCaDefaultsWhenSystemPropertyDefinesSslCertFile() throws Exception {
        NativeBundleInfo bundleInfo = createBundleInfo("linux-aarch64");
        Properties properties = new Properties();
        properties.setProperty(NativeBundleRuntimeConfig.SSL_CERT_FILE, "/custom/ssl-cert.pem");

        Map<String, Path> options = NativeBundleRuntimeConfig.configOptions(bundleInfo, Map.of(), properties);

        assertFalse(options.containsKey(NativeBundleRuntimeConfig.CURL_CA_BUNDLE));
        assertFalse(options.containsKey(NativeBundleRuntimeConfig.SSL_CERT_FILE));
        assertTrue(options.containsKey(NativeBundleRuntimeConfig.PROJ_LIB));
    }

    private static NativeBundleInfo createBundleInfo(String classifier) throws Exception {
        Path root = Files.createTempDirectory("native-runtime-config");
        Path gdalData = Files.createDirectories(root.resolve("share/gdal"));
        Path projData = Files.createDirectories(root.resolve("share/proj"));
        Path driverPath = Files.createDirectories(root.resolve("lib/gdalplugins"));
        Path caBundle = root.resolve("ssl/cacert.pem");
        Files.createDirectories(caBundle.getParent());
        Files.writeString(caBundle, "bundle-ca", StandardCharsets.UTF_8);
        return new NativeBundleInfo(classifier, "3.12.2", root, gdalData, projData, driverPath, caBundle);
    }
}
