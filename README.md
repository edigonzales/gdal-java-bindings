# gdal-java-bindings

Java FFM bindings for GDAL/OGR utilities (`ogr2ogr`, `gdalwarp`, `gdal_translate`) with bundled native libraries.

## Modules

- `gdal-ffm-core`: public Java API (`Gdal.vectorTranslate`, `Gdal.warp`, `Gdal.translate`), native loader, error/progress bridge.
- `gdal-ffm-natives`: classifier JARs that package native GDAL libs + `share/gdal` + `share/proj`.

## Requirements

- JDK 23+
- Native access enabled at runtime:
  - Classpath: `--enable-native-access=ALL-UNNAMED`
  - JPMS: `--enable-native-access=io.github.stefan.gdal.ffm`

## Dependency setup

```kotlin
dependencies {
    implementation("io.github.stefan:gdal-ffm-core:<version>")
    runtimeOnly("io.github.stefan:gdal-ffm-natives:<version>:natives-linux-x86_64")
}
```

Available classifiers:

- `natives-linux-x86_64`
- `natives-linux-aarch64`
- `natives-osx-x86_64`
- `natives-osx-aarch64`
- `natives-windows-x86_64`

## Usage

```java
Gdal.vectorTranslate(
    Path.of("output.gpkg"),
    Path.of("input.geojson"),
    "-f", "GPKG",
    "-overwrite"
);

Gdal.warp(
    Path.of("warped.tif"),
    Path.of("input.tif"),
    "-t_srs", "EPSG:2056",
    "-r", "bilinear"
);

Gdal.translate(
    Path.of("output-cog.tif"),
    Path.of("input.tif"),
    "-of", "COG",
    "-co", "COMPRESS=ZSTD"
);
```

Progress callback:

```java
Gdal.translate(
    Path.of("out.tif"),
    Path.of("in.tif"),
    (complete, message) -> {
        System.out.printf("%.1f%% %s%n", complete * 100.0, message);
        return true;
    },
    "-of", "COG"
);
```

## Regenerating low-level bindings

Low-level bindings are checked into `gdal-ffm-core/src/generated/java`.

```bash
GDAL_INCLUDE_DIR=/path/to/gdal/includes tools/jextract/regenerate.sh
```

If `jextract` is not on `PATH`, override the binary explicitly:

```bash
GDAL_INCLUDE_DIR=/path/to/gdal/includes \
JEXTRACT_BIN=/path/to/jextract \
tools/jextract/regenerate.sh
```

## Native bundle staging

Bundles are staged as runtime-only payloads:

- shared libraries (`.so*`, `.dylib*`, `.dll`)
- optional `gdalplugins`
- `share/gdal` and `share/proj`

CLI binaries (`ogr2ogr`, `gdal_translate`, etc.) are intentionally not shipped in classifier JARs.

1. Review `tools/natives/binaries.lock` (roots + resolver-managed `extra_*` entries).
2. Validate lock integrity:

```bash
tools/natives/verify-lock.sh
```

Optional strict mode (also runs resolver drift check):

```bash
GDAL_FFM_VERIFY_RESOLVER=true tools/natives/verify-lock.sh
```

3. Refresh and validate transitive dependency closure (deterministic):

```bash
tools/natives/refresh-lock-closure.sh --check
```

Resolver debug (bei Constraint-Konflikten):

```bash
tools/natives/refresh-lock-closure.sh --max-backtracks 5000 --debug-resolver
```

4. Ensure `cph` is installed (`conda-package-handling` CLI):

```bash
python -m pip install --user conda-package-handling
```

5. Stage one classifier:

```bash
tools/natives/fetch-and-stage.sh linux-x86_64
```

6. Audit runtime link closure for that classifier:

```bash
tools/natives/audit-runtime-deps.sh linux-x86_64
```

7. Build native classifier jars:

```bash
./gradlew :gdal-ffm-natives:assemble
```

## Developing / Smoke tests

Use this flow when iterating on native bundles and validating that the Java API can load GDAL end-to-end.

1. Refresh lock closure and stage natives for your platform (example: macOS arm64):

```bash
./tools/natives/refresh-lock-closure.sh
./tools/natives/fetch-and-stage.sh osx-aarch64
```

2. Audit staged runtime deps:

```bash
./tools/natives/audit-runtime-deps.sh osx-aarch64
```

3. Rebuild the core JAR and platform-native JAR:

```bash
./gradlew :gdal-ffm-core:jar :gdal-ffm-natives:nativesJarOsxAarch64
```

4. Clear extracted native cache for the exact GDAL/platform tuple:

```bash
rm -rf "$TMPDIR/gdal-ffm/3.11.1/osx-aarch64"
```

Why: `NativeLoader` extracts once into `java.io.tmpdir`. If you do not clear this folder, updated native jars may not be re-extracted.

5. Run a smoke test with native access enabled (source-file mode example):

```bash
java --enable-native-access=ALL-UNNAMED \
  -cp "/Users/stefan/sources/gdal-java-bindings/gdal-ffm-core/build/libs/gdal-ffm-core-0.1.0-SNAPSHOT+gdal3.11.1.jar:/Users/stefan/sources/gdal-java-bindings/gdal-ffm-natives/build/libs/gdal-ffm-natives-0.1.0-SNAPSHOT+gdal3.11.1-natives-osx-aarch64.jar" \
  /Users/stefan/tmp/GdalSmoke.java /Users/stefan/tmp/reclass.tif /Users/stefan/tmpsmoke-out.tif
```

Expected result:

- process exits with code `0`
- output file is created

If you get `UnsatisfiedLinkError` (`Library not loaded`), run:

```bash
./tools/natives/refresh-lock-closure.sh
```

Then repeat the flow. The resolver rewrites `extra_*` entries from pinned roots and avoids manual per-lib edits.

If resolver conflicts appear, rerun with trace:

```bash
./tools/natives/refresh-lock-closure.sh --debug-resolver
```

If you hit a backtrack cap, increase it explicitly:

```bash
./tools/natives/refresh-lock-closure.sh --max-backtracks 5000
```

See `docs/runtime.md` for startup flags and troubleshooting.
