package ch.so.agi.gdal.ffm;

import java.util.List;
import java.util.Map;

/**
 * Open OGR datasource handle.
 */
public interface OgrDataSource extends AutoCloseable {
    List<OgrLayerDefinition> listLayers();

    OgrLayerReader openReader(String layerName, Map<String, String> options);

    OgrLayerWriter openWriter(OgrLayerWriteSpec spec);

    /**
     * Legacy writer signature. Prefer {@link #openWriter(OgrLayerWriteSpec)}.
     */
    @Deprecated
    default OgrLayerWriter openWriter(String layerName, Map<String, String> options) {
        OgrWriteMode writeMode = OgrWriteMode.FAIL_IF_EXISTS;
        if (options != null) {
            writeMode = OgrWriteMode.fromString(options.get(OgrWriterOptions.WRITE_MODE));
        }
        return openWriter(new OgrLayerWriteSpec(layerName, null, List.of()).withWriteMode(writeMode));
    }

    @Override
    void close();
}
