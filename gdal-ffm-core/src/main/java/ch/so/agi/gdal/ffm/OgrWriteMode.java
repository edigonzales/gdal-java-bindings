package ch.so.agi.gdal.ffm;

/**
 * Write behavior when dataset/layer already exists.
 */
public enum OgrWriteMode {
    FAIL_IF_EXISTS,
    OVERWRITE,
    APPEND;

    public static OgrWriteMode fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return FAIL_IF_EXISTS;
        }
        return OgrWriteMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
