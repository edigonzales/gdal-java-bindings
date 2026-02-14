package ch.so.agi.gdal.ffm;

import java.nio.file.Path;

public final class GdalSmoke {
    private GdalSmoke() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected exactly 2 args: <output> <input>");
        }

        Path output = Path.of(args[0]);
        Path input = Path.of(args[1]);

        Gdal.translate(output, input, "-of", "GTiff");
        System.out.println("OK");
    }
}
