package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.Gdal;
import ch.so.agi.gdal.ffm.GdalConfig;
import ch.so.agi.gdal.ffm.Ogr;
import ch.so.agi.gdal.ffm.OgrDataSource;
import ch.so.agi.gdal.ffm.OgrFeature;
import ch.so.agi.gdal.ffm.OgrGeometry;
import ch.so.agi.gdal.ffm.OgrLayerDefinition;
import ch.so.agi.gdal.ffm.OgrLayerReader;
import ch.so.agi.gdal.ffm.OgrLayerWriteSpec;
import ch.so.agi.gdal.ffm.OgrLayerWriter;
import ch.so.agi.gdal.ffm.OgrWriteMode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class GdalPackagedNativeSmoke {
    private static final int WKB_CURVEPOLYGON = 10;
    private static final int WKB_CIRCULARSTRING = 8;
    private static final int EWKB_SRID_FLAG = 0x2000_0000;
    private static final int EWKB_TYPE_MASK = 0x1fff_ffff;
    private static final byte[] CURVE_POLYGON_WKB = HexFormat.of().parseHex(
            "010A00000001000000010800000005000000"
                    + "00000000000000000000000000000000"
                    + "00000000000010400000000000000000"
                    + "00000000000010400000000000001040"
                    + "00000000000000000000000000001040"
                    + "00000000000000000000000000000000"
    );

    private GdalPackagedNativeSmoke() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected exactly 2 args: <raster-output> <raster-input>");
        }

        Path rasterOutput = Path.of(args[0]);
        Path rasterInput = Path.of(args[1]);
        Path workDir = rasterOutput.toAbsolutePath().getParent();
        if (workDir == null) {
            throw new IllegalArgumentException("Raster output must have a parent directory: " + rasterOutput);
        }

        Gdal.rasterConvert(rasterOutput, rasterInput, "--overwrite", "--output-format", "GTiff");
        if (!Files.isRegularFile(rasterOutput)) {
            throw new IllegalStateException("Raster smoke output file is missing: " + rasterOutput);
        }

        Path vectorInput = copyBundledVectorSample(workDir.resolve("ogr-smoke-input.geojson"));
        runOgrReadSmoke(vectorInput);

        Path vectorOutput = workDir.resolve("ogr-smoke-output.gpkg");
        Gdal.vectorTranslate(vectorOutput, vectorInput, "-f", "GPKG", "-overwrite");
        if (!Files.isRegularFile(vectorOutput)) {
            throw new IllegalStateException("Vector smoke output file is missing: " + vectorOutput);
        }
        runOgrReadSmoke(vectorOutput);
        runTrueCurveRoundTripSmoke(workDir.resolve("ogr-curve-smoke-output.gpkg"));

        if (Boolean.getBoolean("gdal.ffm.smoke.expectBundledCaBundle")) {
            assertScopedBundledCaBundle();
        }
        System.out.println("OK");
    }

    private static Path copyBundledVectorSample(Path target) {
        try (InputStream inputStream = GdalPackagedNativeSmoke.class.getResourceAsStream("/smoke/sample.geojson")) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing bundled vector smoke resource: /smoke/sample.geojson");
            }
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to materialize bundled vector smoke resource", e);
        }
    }

    private static void runOgrReadSmoke(Path dataset) {
        try (OgrDataSource dataSource = Ogr.open(dataset)) {
            List<OgrLayerDefinition> layers = dataSource.listLayers();
            if (layers.isEmpty()) {
                throw new IllegalStateException("OGR smoke dataset has no layers: " + dataset);
            }

            OgrLayerDefinition layer = layers.getFirst();
            if (layer.fields().isEmpty()) {
                throw new IllegalStateException("OGR smoke layer has no fields: " + layer.name());
            }

            try (OgrLayerReader reader = dataSource.openReader(layer.name(), Map.of())) {
                Iterator<OgrFeature> iterator = reader.iterator();
                if (!iterator.hasNext()) {
                    throw new IllegalStateException("OGR smoke reader returned no features: " + dataset);
                }

                OgrFeature feature = iterator.next();
                if (feature.attributes().isEmpty()) {
                    throw new IllegalStateException("OGR smoke feature has no attributes: " + dataset);
                }
                if (feature.geometry() == null) {
                    throw new IllegalStateException("OGR smoke feature has no geometry: " + dataset);
                }
            }
        }
    }

    private static void runTrueCurveRoundTripSmoke(Path dataset) {
        try {
            Files.deleteIfExists(dataset);
            try (OgrDataSource dataSource = Ogr.create(dataset, "GPKG", OgrWriteMode.FAIL_IF_EXISTS);
                 OgrLayerWriter writer = dataSource.openWriter(
                         new OgrLayerWriteSpec("curves", WKB_CURVEPOLYGON, List.of()))) {
                writer.write(new OgrFeature(
                        1L,
                        Map.of(),
                        OgrGeometry.fromWkb(CURVE_POLYGON_WKB, 2056)
                ));
            }

            try (OgrDataSource dataSource = Ogr.open(dataset)) {
                OgrLayerDefinition layer = dataSource.listLayers().getFirst();
                if (baseType(layer.geometryType()) != WKB_CURVEPOLYGON) {
                    throw new IllegalStateException(
                            "Expected native OGR layer type CURVEPOLYGON (10), got " + layer.geometryType()
                    );
                }

                try (OgrLayerReader reader = dataSource.openReader(layer.name(), Map.of())) {
                    Iterator<OgrFeature> iterator = reader.iterator();
                    if (!iterator.hasNext()) {
                        throw new IllegalStateException("Native true-curve smoke returned no feature");
                    }

                    OgrGeometry geometry = iterator.next().geometry();
                    if (geometry == null) {
                        throw new IllegalStateException("Native true-curve smoke returned null geometry");
                    }
                    if (geometry.srid().orElse(-1) != 2056) {
                        throw new IllegalStateException(
                                "Native true-curve smoke lost SRID 2056: " + geometry.srid()
                        );
                    }

                    byte[] ewkb = geometry.ewkb();
                    int topRawType = rawType(ewkb, 0);
                    if (baseType(topRawType) != WKB_CURVEPOLYGON) {
                        throw new IllegalStateException(
                                "Native GDAL round-trip stroked CURVEPOLYGON: top-level WKB type="
                                        + baseType(topRawType)
                        );
                    }

                    int topHeaderSize = 5 + ((topRawType & EWKB_SRID_FLAG) != 0 ? 4 : 0);
                    int nestedGeometryOffset = topHeaderSize + 4;
                    int nestedRawType = rawType(ewkb, nestedGeometryOffset);
                    if (baseType(nestedRawType) != WKB_CIRCULARSTRING) {
                        throw new IllegalStateException(
                                "Native GDAL round-trip stroked nested CIRCULARSTRING: nested WKB type="
                                        + baseType(nestedRawType)
                        );
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare native true-curve smoke dataset", e);
        } finally {
            try {
                Files.deleteIfExists(dataset);
            } catch (IOException ignored) {
                // Best-effort cleanup for smoke output.
            }
        }
    }

    private static int rawType(byte[] wkb, int offset) {
        if (offset < 0 || offset + 5 > wkb.length) {
            throw new IllegalStateException(
                    "Invalid WKB geometry offset " + offset + " for payload length " + wkb.length
            );
        }
        ByteOrder order = switch (wkb[offset] & 0xff) {
            case 0 -> ByteOrder.BIG_ENDIAN;
            case 1 -> ByteOrder.LITTLE_ENDIAN;
            default -> throw new IllegalStateException(
                    "Invalid WKB byte-order marker at offset " + offset + ": " + (wkb[offset] & 0xff)
            );
        };
        return ByteBuffer.wrap(wkb, offset + 1, Integer.BYTES).order(order).getInt();
    }

    private static int baseType(int rawType) {
        int type = rawType & EWKB_TYPE_MASK;
        return type >= 1000 ? type % 1000 : type;
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
