package io.github.stefan.gdal.ffm.internal;

import io.github.stefan.gdal.ffm.CplErrorType;
import io.github.stefan.gdal.ffm.GdalException;
import io.github.stefan.gdal.ffm.generated.GdalGenerated;

final class GdalErrors {
    private GdalErrors() {
    }

    static GdalException lastError(String message) {
        int errorNo = GdalGenerated.CPLGetLastErrorNo();
        CplErrorType errorType = CplErrorType.fromCode(GdalGenerated.CPLGetLastErrorType());
        String gdalMessage = CStrings.fromCString(GdalGenerated.CPLGetLastErrorMsg());
        if (gdalMessage.isBlank()) {
            gdalMessage = "No GDAL message available";
        }
        return new GdalException(message, errorType, errorNo, gdalMessage);
    }
}
