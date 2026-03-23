package ch.so.agi.gdal.ffm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DatasetRefTest {
    @Test
    void localPathShouldNormalizeAndExposeAbsolutePath() {
        DatasetRef datasetRef = DatasetRef.local(Path.of("build", "..", "build", "test.tif"));

        assertEquals(DatasetRefType.LOCAL_PATH, datasetRef.type());
        assertTrue(datasetRef.localPath().isAbsolute());
        assertEquals(datasetRef.localPath().toString(), datasetRef.toGdalIdentifier());
    }

    @Test
    void httpUrlShouldUseVsicurlIdentifier() {
        DatasetRef datasetRef = DatasetRef.httpUrl("https://example.com/data.tif");

        assertEquals(DatasetRefType.HTTP_URL, datasetRef.type());
        assertEquals("/vsicurl/https://example.com/data.tif", datasetRef.toGdalIdentifier());
    }

    @Test
    void gdalVsiShouldKeepExplicitIdentifier() {
        DatasetRef datasetRef = DatasetRef.gdalVsi("/vsimem/example.tif");

        assertEquals(DatasetRefType.GDAL_VSI, datasetRef.type());
        assertEquals("/vsimem/example.tif", datasetRef.toGdalIdentifier());
    }

    @Test
    void shouldRejectInvalidHttpScheme() {
        assertThrows(IllegalArgumentException.class, () -> DatasetRef.httpUrl("ftp://example.com/data.tif"));
    }

    @Test
    void shouldRejectInvalidVsiPrefix() {
        assertThrows(IllegalArgumentException.class, () -> DatasetRef.gdalVsi("vsimem/example.tif"));
    }
}
