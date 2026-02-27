package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.OgrOpenOptions;
import ch.so.agi.gdal.ffm.OgrReaderOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class OgrOptions {
    private OgrOptions() {
    }

    static OpenOptions parseOpenOptions(Map<String, String> raw) {
        Objects.requireNonNull(raw, "raw must not be null");

        List<String> allowedDrivers = splitCsvOrSemicolon(raw.get(OgrOpenOptions.ALLOWED_DRIVERS));
        Map<String, String> datasetOptions = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || OgrOpenOptions.ALLOWED_DRIVERS.equals(key)) {
                continue;
            }
            datasetOptions.put(key.trim(), entry.getValue() == null ? "" : entry.getValue().trim());
        }
        return new OpenOptions(List.copyOf(allowedDrivers), Map.copyOf(datasetOptions));
    }

    static ReaderOptions parseReaderOptions(Map<String, String> raw) {
        Objects.requireNonNull(raw, "raw must not be null");

        String attributeFilter = trimToNull(raw.get(OgrReaderOptions.ATTRIBUTE_FILTER));
        BoundingBox bbox = parseBoundingBox(trimToNull(raw.get(OgrReaderOptions.BBOX)));
        String spatialFilterWkt = trimToNull(raw.get(OgrReaderOptions.SPATIAL_FILTER_WKT));
        List<String> selectedFields = splitCsvOrSemicolon(raw.get(OgrReaderOptions.SELECTED_FIELDS));
        Long limit = parseLimit(trimToNull(raw.get(OgrReaderOptions.LIMIT)));

        if (bbox != null && spatialFilterWkt != null) {
            throw new IllegalArgumentException(
                    "Only one spatial filter can be set. Use either '" + OgrReaderOptions.BBOX
                            + "' or '" + OgrReaderOptions.SPATIAL_FILTER_WKT + "'."
            );
        }

        Set<String> selectedFieldsLowercase = new LinkedHashSet<>();
        for (String selectedField : selectedFields) {
            selectedFieldsLowercase.add(selectedField.toLowerCase(Locale.ROOT));
        }

        return new ReaderOptions(
                attributeFilter,
                bbox,
                spatialFilterWkt,
                List.copyOf(selectedFields),
                Set.copyOf(selectedFieldsLowercase),
                limit
        );
    }

    private static BoundingBox parseBoundingBox(String raw) {
        if (raw == null) {
            return null;
        }

        String[] parts = raw.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid bbox format. Expected minX,minY,maxX,maxY");
        }

        try {
            double minX = Double.parseDouble(parts[0].trim());
            double minY = Double.parseDouble(parts[1].trim());
            double maxX = Double.parseDouble(parts[2].trim());
            double maxY = Double.parseDouble(parts[3].trim());
            if (minX > maxX || minY > maxY) {
                throw new IllegalArgumentException("Invalid bbox values. min must be <= max");
            }
            return new BoundingBox(minX, minY, maxX, maxY);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid bbox numbers: " + raw, e);
        }
    }

    private static Long parseLimit(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(raw);
            if (parsed <= 0) {
                throw new IllegalArgumentException("Limit must be > 0");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid limit: " + raw, e);
        }
    }

    private static List<String> splitCsvOrSemicolon(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] split = raw.split("[,;]");
        List<String> values = new ArrayList<>(split.length);
        for (String value : split) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    record OpenOptions(List<String> allowedDrivers, Map<String, String> datasetOptions) {
    }

    record ReaderOptions(
            String attributeFilter,
            BoundingBox bbox,
            String spatialFilterWkt,
            List<String> selectedFields,
            Set<String> selectedFieldsLowercase,
            Long limit
    ) {
    }

    record BoundingBox(double minX, double minY, double maxX, double maxY) {
    }
}
