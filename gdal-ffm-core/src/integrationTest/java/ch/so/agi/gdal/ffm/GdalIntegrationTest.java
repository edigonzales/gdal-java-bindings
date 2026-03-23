package ch.so.agi.gdal.ffm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
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

    @Test
    void infoReturnsJsonForBundledRaster() throws Exception {
        Path input = bundledRaster();

        String json = Gdal.info(input, "-json");

        assertTrue(json.contains("\"driverShortName\""), "Expected GDAL info JSON");
        assertTrue(json.contains("\"size\""), "Expected raster size block in info JSON");
    }

    @Test
    void buildVrtCreatesVirtualMosaic() throws Exception {
        Path input = bundledRaster();
        Path output = outputFile("buildvrt.vrt");

        Gdal.buildVrt(output, List.of(input, input));

        assertTrue(output.toFile().isFile(), "Expected VRT output file to exist: " + output);
        assertTrue(Gdal.info(output, "-json").contains("\"driverShortName\":\"VRT\""));
    }

    @Test
    void rasterizeBurnsGeoJsonIntoRaster() throws Exception {
        Path vectorInput = outputFile("rasterize-input.geojson");
        Path rasterOutput = outputFile("rasterize-output.tif");
        Files.writeString(vectorInput, """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {"class_id": 7},
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[2,2],[2,8],[8,8],[8,2],[2,2]]]
                      }
                    }
                  ]
                }
                """);

        Gdal.rasterize(
                rasterOutput,
                vectorInput,
                "-l", "rasterize-input",
                "-a", "class_id",
                "-te", "0", "0", "10", "10",
                "-tr", "1", "1",
                "-ot", "Byte",
                "-a_nodata", "0",
                "-of", "GTiff"
        );

        assertTrue(rasterOutput.toFile().isFile(), "Expected rasterized output file to exist: " + rasterOutput);
        assertTrue(Gdal.info(rasterOutput, "-json").contains("\"driverShortName\":\"GTiff\""));
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

    private static Path bundledRaster() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                GdalIntegrationTest.class.getResource("/smoke/reclass.tif"),
                "Missing bundled raster smoke resource"
        ).toURI());
    }
}
