package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.Gdal;
import ch.so.agi.gdal.ffm.GdalConfig;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GdalScopedConfigSmoke {
    private GdalScopedConfigSmoke() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected exactly 2 args: <output> <input>");
        }

        Path output = Path.of(args[0]);
        Path input = Path.of(args[1]);

        Gdal.rasterConvert(output, input, "--overwrite", "--output-format", "GTiff");
        if (Boolean.getBoolean("gdal.ffm.smoke.expectBundledCaBundle")) {
            assertScopedBundledCaBundle();
        }
        System.out.println("OK");
    }

    private static void assertScopedBundledCaBundle() {
        String previousCurlCaBundle = GdalNative.getThreadLocalConfigOption(NativeBundleRuntimeConfig.CURL_CA_BUNDLE);
        String previousSslCertFile = GdalNative.getThreadLocalConfigOption(NativeBundleRuntimeConfig.SSL_CERT_FILE);

        try (GdalConfigScope.ScopedConfigHandle ignored = GdalConfigScope.applyScoped(GdalConfig.empty())) {
            String curlCaBundle = GdalNative.getThreadLocalConfigOption(NativeBundleRuntimeConfig.CURL_CA_BUNDLE);
            String sslCertFile = GdalNative.getThreadLocalConfigOption(NativeBundleRuntimeConfig.SSL_CERT_FILE);
            if (curlCaBundle == null || curlCaBundle.isBlank()) {
                throw new IllegalStateException("Scoped CURL_CA_BUNDLE thread-local option is not set");
            }
            if (sslCertFile == null || sslCertFile.isBlank()) {
                throw new IllegalStateException("Scoped SSL_CERT_FILE thread-local option is not set");
            }
            if (!curlCaBundle.equals(sslCertFile)) {
                throw new IllegalStateException(
                        "Scoped CURL_CA_BUNDLE and SSL_CERT_FILE must resolve to the same bundle"
                );
            }

            Path caBundle = Path.of(curlCaBundle);
            if (!caBundle.isAbsolute()) {
                throw new IllegalStateException("Bundled CA path must be absolute: " + caBundle);
            }
            if (!caBundle.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath())) {
                throw new IllegalStateException("Bundled CA path must live below java.io.tmpdir: " + caBundle);
            }
            if (!Files.isRegularFile(caBundle)) {
                throw new IllegalStateException("Bundled CA file is missing: " + caBundle);
            }
            if (!caBundle.endsWith("ssl/cacert.pem")) {
                throw new IllegalStateException("Bundled CA file must end with ssl/cacert.pem: " + caBundle);
            }
        }

        String restoredCurlCaBundle = GdalNative.getThreadLocalConfigOption(NativeBundleRuntimeConfig.CURL_CA_BUNDLE);
        String restoredSslCertFile = GdalNative.getThreadLocalConfigOption(NativeBundleRuntimeConfig.SSL_CERT_FILE);
        if (!equalsNullable(previousCurlCaBundle, restoredCurlCaBundle)) {
            throw new IllegalStateException("Scoped CURL_CA_BUNDLE did not restore the previous thread-local value");
        }
        if (!equalsNullable(previousSslCertFile, restoredSslCertFile)) {
            throw new IllegalStateException("Scoped SSL_CERT_FILE did not restore the previous thread-local value");
        }
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
