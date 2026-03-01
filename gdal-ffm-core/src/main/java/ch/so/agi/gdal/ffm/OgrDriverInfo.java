package ch.so.agi.gdal.ffm;

import java.util.List;
import java.util.Objects;

/**
 * Writable OGR driver metadata.
 */
public record OgrDriverInfo(
        String shortName,
        String longName,
        List<String> extensions,
        boolean canCreate,
        boolean isVector
) {
    public OgrDriverInfo {
        Objects.requireNonNull(shortName, "shortName must not be null");
        Objects.requireNonNull(longName, "longName must not be null");
        Objects.requireNonNull(extensions, "extensions must not be null");
        extensions = List.copyOf(extensions);
    }
}
