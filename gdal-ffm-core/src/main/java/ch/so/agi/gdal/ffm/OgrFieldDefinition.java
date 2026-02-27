package ch.so.agi.gdal.ffm;

import java.util.Objects;

/**
 * OGR field metadata.
 */
public record OgrFieldDefinition(String name, OgrFieldType type) {
    public OgrFieldDefinition {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }
}
