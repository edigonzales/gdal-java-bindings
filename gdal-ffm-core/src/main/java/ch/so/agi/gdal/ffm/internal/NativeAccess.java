package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.Gdal;

final class NativeAccess {
    private NativeAccess() {
    }

    static void ensureEnabled() {
        Module module = Gdal.class.getModule();
        if (module.isNativeAccessEnabled()) {
            return;
        }

        String moduleToken = module.isNamed() ? module.getName() : "ALL-UNNAMED";
        throw new IllegalStateException(
                "Native access is not enabled. Start the JVM with --enable-native-access=" + moduleToken
        );
    }
}
