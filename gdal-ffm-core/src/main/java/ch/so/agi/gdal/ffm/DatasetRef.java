package ch.so.agi.gdal.ffm;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

public record DatasetRef(DatasetRefType type, String identifier) {
    private static final String VSICURL_PREFIX = "/vsicurl/";

    public DatasetRef {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(identifier, "identifier must not be null");

        identifier = identifier.trim();
        if (identifier.isEmpty()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }

        switch (type) {
            case LOCAL_PATH -> identifier = Path.of(identifier).toAbsolutePath().normalize().toString();
            case HTTP_URL -> validateHttpUrl(identifier);
            case GDAL_VSI -> validateVsi(identifier);
            default -> throw new IllegalStateException("Unhandled dataset ref type: " + type);
        }
    }

    public static DatasetRef local(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        return new DatasetRef(DatasetRefType.LOCAL_PATH, path.toAbsolutePath().normalize().toString());
    }

    public static DatasetRef local(String path) {
        Objects.requireNonNull(path, "path must not be null");
        return local(Path.of(path));
    }

    public static DatasetRef httpUrl(String url) {
        return new DatasetRef(DatasetRefType.HTTP_URL, url);
    }

    public static DatasetRef gdalVsi(String identifier) {
        return new DatasetRef(DatasetRefType.GDAL_VSI, identifier);
    }

    public boolean isLocalPath() {
        return type == DatasetRefType.LOCAL_PATH;
    }

    public Path localPath() {
        if (!isLocalPath()) {
            throw new IllegalStateException("DatasetRef is not a local path: " + type);
        }
        return Path.of(identifier);
    }

    public String toGdalIdentifier() {
        return switch (type) {
            case LOCAL_PATH -> identifier;
            case HTTP_URL -> VSICURL_PREFIX + identifier;
            case GDAL_VSI -> identifier;
        };
    }

    private static void validateHttpUrl(String identifier) {
        try {
            URI uri = new URI(identifier);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("HTTP dataset references must use http or https: " + identifier);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid HTTP dataset reference: " + identifier, e);
        }
    }

    private static void validateVsi(String identifier) {
        if (!identifier.startsWith("/vsi")) {
            throw new IllegalArgumentException("Explicit GDAL/VSI paths must start with /vsi: " + identifier);
        }
    }
}
