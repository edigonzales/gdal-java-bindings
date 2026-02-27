package ch.so.agi.gdal.ffm;

/**
 * Key constants for {@link OgrDataSource#openReader(String, java.util.Map)} options.
 */
public final class OgrReaderOptions {
    /**
     * OGR attribute filter expression.
     */
    public static final String ATTRIBUTE_FILTER = "attributeFilter";

    /**
     * Comma-separated bbox: minX,minY,maxX,maxY.
     */
    public static final String BBOX = "bbox";

    /**
     * Spatial filter geometry as WKT.
     */
    public static final String SPATIAL_FILTER_WKT = "spatialFilterWkt";

    /**
     * Comma/semicolon-separated list of fields to include.
     */
    public static final String SELECTED_FIELDS = "selectedFields";

    /**
     * Maximum number of features to emit.
     */
    public static final String LIMIT = "limit";

    private OgrReaderOptions() {
    }
}
