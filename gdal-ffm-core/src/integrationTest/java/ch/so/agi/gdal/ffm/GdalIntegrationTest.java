package ch.so.agi.gdal.ffm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class GdalIntegrationTest {
    @Test
    void vectorTranslateGeoJsonToGpkg() {
        Path input = testData("sample.geojson");
        Path output = outputFile("vector-translate.gpkg");

        Gdal.vectorTranslate(output, input, "-f", "GPKG", "-overwrite");

        assertTrue(output.toFile().isFile(), "Expected output file to exist: " + output);
    }

    @Test
    void translateProducesCog() {
        Path input = testData("sample.tif");
        Path output = outputFile("translate-cog.tif");

        Gdal.translate(output, input, "-of", "COG", "-co", "COMPRESS=ZSTD");

        assertTrue(output.toFile().isFile(), "Expected output file to exist: " + output);
    }

    @Test
    void warpReprojectsRaster() {
        Path input = testData("sample.tif");
        Path output = outputFile("warp-reprojected.tif");

        Gdal.warp(output, input, "-t_srs", "EPSG:2056", "-r", "bilinear");

        assertTrue(output.toFile().isFile(), "Expected output file to exist: " + output);
    }

    private static Path testData(String fileName) {
        String testDataRoot = System.getenv("GDAL_FFM_TESTDATA_DIR");
        Assumptions.assumeTrue(testDataRoot != null && !testDataRoot.isBlank(), "GDAL_FFM_TESTDATA_DIR is not set");

        Path path = Path.of(testDataRoot, fileName);
        Assumptions.assumeTrue(path.toFile().isFile(), "Missing integration test file: " + path);
        return path;
    }

    private static Path outputFile(String fileName) {
        String outputRoot = System.getenv("GDAL_FFM_TEST_OUTPUT_DIR");
        Path directory = (outputRoot == null || outputRoot.isBlank())
                ? Path.of("build", "integration-test-output")
                : Path.of(outputRoot);
        directory.toFile().mkdirs();
        return directory.resolve(fileName);
    }
}
