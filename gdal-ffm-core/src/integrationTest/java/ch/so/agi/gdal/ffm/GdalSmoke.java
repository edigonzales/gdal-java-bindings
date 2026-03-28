package ch.so.agi.gdal.ffm;

import java.nio.file.Path;

public final class GdalSmoke {
    private GdalSmoke() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected exactly 2 args: <output> <input>");
        }

        Path output = Path.of(args[0]);
        Path input = Path.of(args[1]);

        Gdal.rasterConvert(output, input, "--overwrite", "--output-format", "GTiff");
        if (Boolean.getBoolean("gdal.ffm.smoke.expectBundledCaBundle")) {
            assertBundledCaBundle();
        }
        System.out.println("OK");
    }

    private static void assertBundledCaBundle() {
        String curlCaBundle = System.getProperty("CURL_CA_BUNDLE");
        String sslCertFile = System.getProperty("SSL_CERT_FILE");
        if (curlCaBundle == null || curlCaBundle.isBlank()) {
            throw new IllegalStateException("CURL_CA_BUNDLE system property is not set");
        }
        if (sslCertFile == null || sslCertFile.isBlank()) {
            throw new IllegalStateException("SSL_CERT_FILE system property is not set");
        }
        if (!curlCaBundle.equals(sslCertFile)) {
            throw new IllegalStateException("CURL_CA_BUNDLE and SSL_CERT_FILE must resolve to the same bundle");
        }

        Path caBundle = Path.of(curlCaBundle);
        if (!caBundle.isAbsolute()) {
            throw new IllegalStateException("Bundled CA path must be absolute: " + caBundle);
        }
        if (!caBundle.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath())) {
            throw new IllegalStateException("Bundled CA path must live below java.io.tmpdir: " + caBundle);
        }
        if (!java.nio.file.Files.isRegularFile(caBundle)) {
            throw new IllegalStateException("Bundled CA file is missing: " + caBundle);
        }
        if (!caBundle.endsWith("ssl/cacert.pem")) {
            throw new IllegalStateException("Bundled CA file must end with ssl/cacert.pem: " + caBundle);
        }
    }
}
