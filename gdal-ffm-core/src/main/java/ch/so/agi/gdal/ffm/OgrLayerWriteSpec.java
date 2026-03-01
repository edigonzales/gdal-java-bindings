package ch.so.agi.gdal.ffm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Layer creation/write specification for OGR streaming exports.
 */
public record OgrLayerWriteSpec(
        String layerName,
        Integer geometryTypeCode,
        List<OgrFieldDefinition> fields,
        OgrWriteMode writeMode,
        Map<String, String> datasetCreationOptions,
        Map<String, String> layerCreationOptions,
        String fidFieldName,
        String geometryFieldName
) {
    public OgrLayerWriteSpec {
        Objects.requireNonNull(layerName, "layerName must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        Objects.requireNonNull(writeMode, "writeMode must not be null");
        Objects.requireNonNull(datasetCreationOptions, "datasetCreationOptions must not be null");
        Objects.requireNonNull(layerCreationOptions, "layerCreationOptions must not be null");

        layerName = layerName.trim();
        if (layerName.isEmpty()) {
            throw new IllegalArgumentException("layerName must not be blank");
        }

        if (geometryTypeCode != null && geometryTypeCode < 0) {
            throw new IllegalArgumentException("geometryTypeCode must be >= 0 when set");
        }

        fields = List.copyOf(fields);
        datasetCreationOptions = Map.copyOf(datasetCreationOptions);
        layerCreationOptions = Map.copyOf(layerCreationOptions);

        if (fidFieldName != null && fidFieldName.isBlank()) {
            fidFieldName = null;
        }
        if (geometryFieldName != null && geometryFieldName.isBlank()) {
            geometryFieldName = null;
        }
    }

    public OgrLayerWriteSpec(String layerName, Integer geometryTypeCode, List<OgrFieldDefinition> fields) {
        this(
                layerName,
                geometryTypeCode,
                fields,
                OgrWriteMode.FAIL_IF_EXISTS,
                Map.of(),
                Map.of(),
                null,
                null
        );
    }

    public OgrLayerWriteSpec withWriteMode(OgrWriteMode mode) {
        return new OgrLayerWriteSpec(
                layerName,
                geometryTypeCode,
                fields,
                mode,
                datasetCreationOptions,
                layerCreationOptions,
                fidFieldName,
                geometryFieldName
        );
    }
}
