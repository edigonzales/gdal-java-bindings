package ch.so.agi.gdal.ffm.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NativeLoaderTest {
    private static final String CLASSIFIER = "linux-x86_64";

    @Test
    void failsIfMultipleNativeManifestsExistForSameClassifier() throws Exception {
        Path firstRoot = createManifestRoot("native-loader-first");
        Path secondRoot = createManifestRoot("native-loader-second");

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[] { firstRoot.toUri().toURL(), secondRoot.toUri().toURL() },
                null
        )) {
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> invokeFindManifest(classLoader));
            assertTrue(error.getMessage().contains("Multiple bundled GDAL native resources found"));
            assertTrue(error.getMessage().contains("gdal-ffm-natives"));
            assertTrue(error.getMessage().contains("gdal-ffm-natives-swiss"));
        }
    }

    @Test
    void resolvesSingleNativeManifest() throws Exception {
        Path onlyRoot = createManifestRoot("native-loader-only");

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] { onlyRoot.toUri().toURL() }, null)) {
            URL manifest = invokeFindManifest(classLoader);
            assertEquals("file", manifest.getProtocol());
            assertTrue(manifest.toString().endsWith("/META-INF/gdal-native/" + CLASSIFIER + "/manifest.json"));
        }
    }

    private static URL invokeFindManifest(URLClassLoader classLoader) {
        Thread current = Thread.currentThread();
        ClassLoader previous = current.getContextClassLoader();
        current.setContextClassLoader(classLoader);
        try {
            Method method = NativeLoader.class.getDeclaredMethod("findManifest", String.class, String.class);
            method.setAccessible(true);
            return (URL) method.invoke(null, "META-INF/gdal-native/" + CLASSIFIER, CLASSIFIER);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        } finally {
            current.setContextClassLoader(previous);
        }
    }

    private static Path createManifestRoot(String prefix) throws Exception {
        Path root = Files.createTempDirectory(prefix);
        Path manifest = root.resolve("META-INF/gdal-native/" + CLASSIFIER + "/manifest.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, "{}", StandardCharsets.UTF_8);
        return root;
    }
}
