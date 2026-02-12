package io.github.stefan.gdal.ffm;

@FunctionalInterface
public interface ProgressCallback {
    /**
     * @param complete progress in range {@code [0.0, 1.0]}
     * @param message informational text from GDAL, can be empty
     * @return {@code true} to continue, {@code false} to abort
     */
    boolean onProgress(double complete, String message);
}
