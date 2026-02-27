package ch.so.agi.gdal.ffm;

/**
 * Feature writer for an OGR layer.
 */
public interface OgrLayerWriter extends AutoCloseable {
    void write(OgrFeature feature);

    @Override
    void close();
}
