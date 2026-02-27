package ch.so.agi.gdal.ffm;

/**
 * Key constants for {@link Ogr#open(java.nio.file.Path, java.util.Map)}.
 */
public final class OgrOpenOptions {
    /**
     * Comma/semicolon-separated list of driver names that may open the dataset.
     */
    public static final String ALLOWED_DRIVERS = "allowedDrivers";

    private OgrOpenOptions() {
    }
}
