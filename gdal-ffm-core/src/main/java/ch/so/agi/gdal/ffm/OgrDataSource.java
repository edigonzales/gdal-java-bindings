package ch.so.agi.gdal.ffm;

import java.util.Map;

/**
 * Open OGR datasource handle.
 */
public interface OgrDataSource extends AutoCloseable {
    OgrLayerReader openReader(String layerName, Map<String, String> options);

    OgrLayerWriter openWriter(String layerName, Map<String, String> options);

    @Override
    void close();
}
