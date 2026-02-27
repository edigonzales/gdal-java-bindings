package ch.so.agi.gdal.ffm;

/**
 * OGR field type codes.
 */
public enum OgrFieldType {
    INTEGER(0),
    INTEGER_LIST(1),
    REAL(2),
    REAL_LIST(3),
    STRING(4),
    STRING_LIST(5),
    WIDE_STRING(6),
    WIDE_STRING_LIST(7),
    BINARY(8),
    DATE(9),
    TIME(10),
    DATETIME(11),
    INTEGER64(12),
    INTEGER64_LIST(13),
    UNKNOWN(-1);

    private final int nativeCode;

    OgrFieldType(int nativeCode) {
        this.nativeCode = nativeCode;
    }

    public int nativeCode() {
        return nativeCode;
    }

    public static OgrFieldType fromNativeCode(int nativeCode) {
        for (OgrFieldType value : values()) {
            if (value.nativeCode == nativeCode) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
