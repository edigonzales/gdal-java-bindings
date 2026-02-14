package ch.so.agi.gdal.ffm.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

final class NativeLoader {
    private static final String RESOURCE_ROOT = "META-INF/gdal-native";
    private static final AtomicReference<NativeBundleInfo> LOADED = new AtomicReference<>();
    private static final Object LOCK = new Object();

    private NativeLoader() {
    }

    static NativeBundleInfo load() {
        NativeBundleInfo existing = LOADED.get();
        if (existing != null) {
            return existing;
        }

        synchronized (LOCK) {
            existing = LOADED.get();
            if (existing != null) {
                return existing;
            }
            NativeBundleInfo loaded = loadOnce();
            LOADED.set(loaded);
            return loaded;
        }
    }

    private static NativeBundleInfo loadOnce() {
        NativePlatform platform = NativePlatform.current();
        String classifier = platform.classifier();
        String prefix = RESOURCE_ROOT + "/" + classifier;

        URL manifestUrl = findManifest(prefix, classifier);
        NativeManifest manifest = NativeManifest.parse(readUtf8(manifestUrl));

        Path extractionRoot = extractionRoot(manifest.bundleVersion(), classifier);
        Path marker = extractionRoot.resolve(".extract-complete");
        if (!Files.exists(marker)) {
            extractBundle(manifestUrl, extractionRoot);
            try {
                Files.createDirectories(extractionRoot);
                Files.writeString(marker, manifest.bundleVersion(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create extraction marker: " + marker, e);
            }
        }

        for (String preload : manifest.preloadLibraries()) {
            loadLibrary(extractionRoot, preload, "preloadLibraries");
        }
        loadLibrary(extractionRoot, manifest.entryLibrary(), "entryLibrary");

        return new NativeBundleInfo(
                classifier,
                manifest.bundleVersion(),
                extractionRoot,
                resolveOptional(extractionRoot, manifest.gdalDataPath()),
                resolveOptional(extractionRoot, manifest.projDataPath()),
                resolveOptional(extractionRoot, manifest.driverPath())
        );
    }

    private static URL findManifest(String prefix, String classifier) {
        String manifestResource = prefix + "/manifest.json";
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = NativeLoader.class.getClassLoader();
            }
            Enumeration<URL> urls = classLoader.getResources(manifestResource);
            if (!urls.hasMoreElements()) {
                throw new IllegalStateException(
                        "No bundled GDAL native resources found for classifier '" + classifier + "'. "
                                + "Add runtime dependency ch.so.agi:gdal-ffm-natives:<VERSION>:natives-"
                                + classifier
                );
            }

            URL first = urls.nextElement();
            if (urls.hasMoreElements()) {
                StringBuilder matches = new StringBuilder(first.toString());
                while (urls.hasMoreElements()) {
                    matches.append(", ").append(urls.nextElement());
                }
                throw new IllegalStateException(
                        "Multiple bundled GDAL native resources found for classifier '" + classifier + "': "
                                + matches
                                + ". Add exactly one runtime dependency, either "
                                + "ch.so.agi:gdal-ffm-natives:<VERSION>:natives-" + classifier
                                + " or ch.so.agi:gdal-ffm-natives-swiss:<VERSION>:natives-" + classifier
                );
            }
            return first;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan classpath for " + manifestResource, e);
        }
    }

    private static String readUtf8(URL url) {
        try (InputStream inputStream = url.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read native manifest: " + url, e);
        }
    }

    private static Path extractionRoot(String bundleVersion, String classifier) {
        String javaTmp = System.getProperty("java.io.tmpdir");
        return Path.of(javaTmp, "gdal-ffm", bundleVersion, classifier);
    }

    private static void extractBundle(URL manifestUrl, Path extractionRoot) {
        try {
            Files.createDirectories(extractionRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create extraction directory: " + extractionRoot, e);
        }

        String protocol = manifestUrl.getProtocol();
        if ("jar".equals(protocol)) {
            extractFromJar(manifestUrl, extractionRoot);
            return;
        }
        if ("file".equals(protocol)) {
            extractFromFileTree(manifestUrl, extractionRoot);
            return;
        }
        throw new IllegalStateException("Unsupported manifest URL protocol for native extraction: " + protocol);
    }

    private static void extractFromJar(URL manifestUrl, Path extractionRoot) {
        try {
            JarURLConnection connection = (JarURLConnection) manifestUrl.openConnection();
            connection.setUseCaches(false);

            String entryName = connection.getEntryName();
            String prefix = entryName.substring(0, entryName.length() - "manifest.json".length());

            try (JarFile jarFile = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
                        continue;
                    }

                    String relativeName = entry.getName().substring(prefix.length());
                    if (relativeName.isBlank()) {
                        continue;
                    }

                    Path target = safeResolve(extractionRoot, relativeName);
                    Files.createDirectories(target.getParent());
                    try (InputStream inputStream = jarFile.getInputStream(entry)) {
                        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract native bundle from JAR: " + manifestUrl, e);
        }
    }

    private static void extractFromFileTree(URL manifestUrl, Path extractionRoot) {
        Path manifestPath;
        try {
            URI uri = manifestUrl.toURI();
            manifestPath = Path.of(uri);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid manifest URL: " + manifestUrl, e);
        }

        Path sourceRoot = manifestPath.getParent();
        if (sourceRoot == null || !Files.exists(sourceRoot)) {
            throw new IllegalStateException("Native resource source path does not exist: " + manifestPath);
        }

        try (var stream = Files.walk(sourceRoot)) {
            stream.filter(Files::isRegularFile).forEach(source -> {
                Path relative = sourceRoot.relativize(source);
                Path target = safeResolve(extractionRoot, relative.toString());
                try {
                    Files.createDirectories(Objects.requireNonNull(target.getParent()));
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to copy native resource " + source + " to " + target, e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to traverse native resource tree: " + sourceRoot, e);
        }
    }

    private static void loadLibrary(Path extractionRoot, String relativePath, String sourceField) {
        Path libPath = safeResolve(extractionRoot, relativePath);
        if (!Files.exists(libPath)) {
            throw new IllegalStateException(
                    "Native library listed in " + sourceField + " is missing: " + relativePath
            );
        }
        System.load(libPath.toString());
    }

    private static Path resolveOptional(Path extractionRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path path = safeResolve(extractionRoot, relativePath);
        return Files.exists(path) ? path : null;
    }

    private static Path safeResolve(Path base, String relative) {
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalStateException("Invalid path in native manifest: " + relative);
        }
        return resolved;
    }
}
