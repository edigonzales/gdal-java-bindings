package ch.so.agi.gdal.ffm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Neutral geometry container for vector streaming APIs.
 * <p>
 * Internally stores EWKB bytes (WKB with optional SRID flag/value). If an SRID is provided,
 * the EWKB payload is normalized to include it.
 */
public final class OgrGeometry {
    private static final int EWKB_SRID_FLAG = 0x2000_0000;
    private static final int WKB_HEADER_SIZE = 5;
    private static final int EWKB_SRID_SIZE = 4;

    private final byte[] ewkb;
    private final Integer srid;

    private OgrGeometry(byte[] ewkb, Integer srid) {
        this.ewkb = ewkb;
        this.srid = srid;
    }

    public static OgrGeometry fromEwkb(byte[] ewkb) {
        Objects.requireNonNull(ewkb, "ewkb must not be null");
        byte[] copy = Arrays.copyOf(ewkb, ewkb.length);
        OptionalInt parsedSrid = extractSrid(copy);
        Integer srid = parsedSrid.isPresent() ? parsedSrid.getAsInt() : null;
        return new OgrGeometry(copy, srid);
    }

    public static OgrGeometry fromWkb(byte[] wkb) {
        Objects.requireNonNull(wkb, "wkb must not be null");
        return new OgrGeometry(Arrays.copyOf(wkb, wkb.length), null);
    }

    public static OgrGeometry fromWkb(byte[] wkb, int srid) {
        Objects.requireNonNull(wkb, "wkb must not be null");
        if (srid < 0) {
            throw new IllegalArgumentException("srid must be >= 0");
        }
        byte[] ewkb = ensureSridInEwkb(wkb, srid);
        return new OgrGeometry(ewkb, srid);
    }

    public byte[] ewkb() {
        return Arrays.copyOf(ewkb, ewkb.length);
    }

    public OptionalInt srid() {
        return srid == null ? OptionalInt.empty() : OptionalInt.of(srid);
    }

    private static OptionalInt extractSrid(byte[] candidate) {
        if (candidate.length < WKB_HEADER_SIZE) {
            return OptionalInt.empty();
        }
        ByteOrder order = byteOrder(candidate[0]);
        ByteBuffer buffer = ByteBuffer.wrap(candidate).order(order);
        int rawType = buffer.getInt(1);
        if ((rawType & EWKB_SRID_FLAG) == 0) {
            return OptionalInt.empty();
        }
        if (candidate.length < WKB_HEADER_SIZE + EWKB_SRID_SIZE) {
            return OptionalInt.empty();
        }
        int srid = buffer.getInt(WKB_HEADER_SIZE);
        return srid >= 0 ? OptionalInt.of(srid) : OptionalInt.empty();
    }

    private static byte[] ensureSridInEwkb(byte[] wkbOrEwkb, int srid) {
        if (wkbOrEwkb.length < WKB_HEADER_SIZE) {
            throw new IllegalArgumentException("WKB/EWKB payload is too short");
        }
        byte orderMarker = wkbOrEwkb[0];
        ByteOrder order = byteOrder(orderMarker);
        ByteBuffer in = ByteBuffer.wrap(wkbOrEwkb).order(order);
        int rawType = in.getInt(1);
        boolean hasSrid = (rawType & EWKB_SRID_FLAG) != 0;

        if (hasSrid) {
            byte[] updated = Arrays.copyOf(wkbOrEwkb, wkbOrEwkb.length);
            ByteBuffer out = ByteBuffer.wrap(updated).order(order);
            out.putInt(WKB_HEADER_SIZE, srid);
            return updated;
        }

        byte[] result = new byte[wkbOrEwkb.length + EWKB_SRID_SIZE];
        result[0] = orderMarker;
        ByteBuffer out = ByteBuffer.wrap(result).order(order);
        out.putInt(1, rawType | EWKB_SRID_FLAG);
        out.putInt(WKB_HEADER_SIZE, srid);
        System.arraycopy(wkbOrEwkb, WKB_HEADER_SIZE, result, WKB_HEADER_SIZE + EWKB_SRID_SIZE,
                wkbOrEwkb.length - WKB_HEADER_SIZE);
        return result;
    }

    private static ByteOrder byteOrder(byte marker) {
        return switch (marker) {
            case 0 -> ByteOrder.BIG_ENDIAN;
            case 1 -> ByteOrder.LITTLE_ENDIAN;
            default -> throw new IllegalArgumentException("Unsupported WKB byte order marker: " + marker);
        };
    }
}
