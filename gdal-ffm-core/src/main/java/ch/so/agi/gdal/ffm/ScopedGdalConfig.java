package ch.so.agi.gdal.ffm;

import ch.so.agi.gdal.ffm.internal.GdalConfigScope;
import java.util.Objects;

public final class ScopedGdalConfig implements AutoCloseable {
    private final GdalConfigScope.ScopedConfigHandle delegate;

    private ScopedGdalConfig(GdalConfigScope.ScopedConfigHandle delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    public static ScopedGdalConfig apply(GdalConfig config) {
        return new ScopedGdalConfig(GdalConfigScope.applyScoped(config));
    }

    @Override
    public void close() {
        delegate.close();
    }
}
