package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.CplErrorType;
import ch.so.agi.gdal.ffm.GdalException;
import ch.so.agi.gdal.ffm.generated.GdalGenerated;

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
