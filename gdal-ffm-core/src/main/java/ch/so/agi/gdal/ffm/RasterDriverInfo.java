package ch.so.agi.gdal.ffm;

import java.util.List;
import java.util.Objects;

/**
 * Writable raster driver metadata.
 */
public record RasterDriverInfo(
        String shortName,
        String longName,
        List<String> extensions,
        boolean canCreate,
        boolean canCreateCopy
) {
    public RasterDriverInfo {
        Objects.requireNonNull(shortName, "shortName must not be null");
        Objects.requireNonNull(longName, "longName must not be null");
        Objects.requireNonNull(extensions, "extensions must not be null");
        extensions = List.copyOf(extensions);
    }
}
