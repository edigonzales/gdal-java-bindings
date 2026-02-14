package ch.so.agi.gdal.ffm;

public final class GdalException extends RuntimeException {
    private final int errorNo;
    private final CplErrorType errorType;
    private final String gdalMessage;

    public GdalException(String message, CplErrorType errorType, int errorNo, String gdalMessage) {
        super(messageWithDetail(message, errorType, errorNo, gdalMessage));
        this.errorNo = errorNo;
        this.errorType = errorType;
        this.gdalMessage = gdalMessage;
    }

    public GdalException(String message, CplErrorType errorType, int errorNo, String gdalMessage, Throwable cause) {
        super(messageWithDetail(message, errorType, errorNo, gdalMessage), cause);
        this.errorNo = errorNo;
        this.errorType = errorType;
        this.gdalMessage = gdalMessage;
    }

    public int getErrorNo() {
        return errorNo;
    }

    public CplErrorType getErrorType() {
        return errorType;
    }

    public String getGdalMessage() {
        return gdalMessage;
    }

    private static String messageWithDetail(String message, CplErrorType errorType, int errorNo, String gdalMessage) {
        String detail = "type=" + errorType + ", no=" + errorNo + ", gdal='" + gdalMessage + "'";
        return message + " (" + detail + ")";
    }
}
