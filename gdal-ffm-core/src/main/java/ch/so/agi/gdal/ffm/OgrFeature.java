package ch.so.agi.gdal.ffm;

import java.util.Map;
import java.util.Objects;

/**
 * Neutral feature DTO for stream-oriented OGR integrations.
 */
public record OgrFeature(long fid, Map<String, Object> attributes, OgrGeometry geometry) {
    public OgrFeature {
        Objects.requireNonNull(attributes, "attributes must not be null");
    }
}
