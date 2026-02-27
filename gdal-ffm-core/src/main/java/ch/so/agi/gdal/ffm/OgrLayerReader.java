package ch.so.agi.gdal.ffm;

import java.util.Iterator;

/**
 * Sequential feature reader for an OGR layer.
 */
public interface OgrLayerReader extends AutoCloseable, Iterable<OgrFeature> {
    @Override
    Iterator<OgrFeature> iterator();

    @Override
    void close();
}
