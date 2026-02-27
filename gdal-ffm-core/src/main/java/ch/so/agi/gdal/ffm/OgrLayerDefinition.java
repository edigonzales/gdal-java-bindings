package ch.so.agi.gdal.ffm;

import java.util.List;
import java.util.Objects;

/**
 * OGR layer metadata.
 */
public record OgrLayerDefinition(String name, int geometryType, List<OgrFieldDefinition> fields) {
    public OgrLayerDefinition {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        fields = List.copyOf(fields);
    }
}
