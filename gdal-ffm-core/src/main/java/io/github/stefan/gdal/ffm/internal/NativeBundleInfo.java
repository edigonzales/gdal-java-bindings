package io.github.stefan.gdal.ffm.internal;

import java.nio.file.Path;

record NativeBundleInfo(
        String classifier,
        String bundleVersion,
        Path extractionRoot,
        Path gdalData,
        Path projData,
        Path driverPath
) {
}
