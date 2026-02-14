package ch.so.agi.gdal.ffm;

import ch.so.agi.gdal.ffm.internal.GdalRuntime;
import java.nio.file.Path;
import java.util.Objects;

public final class Gdal {
    private Gdal() {
    }

    public static void vectorTranslate(Path dest, Path src, String... args) {
        vectorTranslate(dest, src, null, args);
    }

    public static void vectorTranslate(Path dest, Path src, ProgressCallback progress, String... args) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        GdalRuntime.vectorTranslate(dest, src, progress, args);
    }

    public static void warp(Path dest, Path src, String... args) {
        warp(dest, src, null, args);
    }

    public static void warp(Path dest, Path src, ProgressCallback progress, String... args) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        GdalRuntime.warp(dest, src, progress, args);
    }

    public static void translate(Path dest, Path src, String... args) {
        translate(dest, src, null, args);
    }

    public static void translate(Path dest, Path src, ProgressCallback progress, String... args) {
        Objects.requireNonNull(dest, "dest must not be null");
        Objects.requireNonNull(src, "src must not be null");
        GdalRuntime.translate(dest, src, progress, args);
    }
}
