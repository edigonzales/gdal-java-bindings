package ch.so.agi.gdal.ffm;

/**
 * Mirrors GDAL's {@code CPLErr} enum values.
 */
public enum CplErrorType {
    NONE(0),
    DEBUG(1),
    WARNING(2),
    FAILURE(3),
    FATAL(4),
    UNKNOWN(Integer.MIN_VALUE);

    private final int code;

    CplErrorType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static CplErrorType fromCode(int code) {
        return switch (code) {
            case 0 -> NONE;
            case 1 -> DEBUG;
            case 2 -> WARNING;
            case 3 -> FAILURE;
            case 4 -> FATAL;
            default -> UNKNOWN;
        };
    }
}
