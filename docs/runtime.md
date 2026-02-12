# Runtime Notes

## Native access

FFM calls require native access for the module that performs the call.

- Classpath use:
  - `--enable-native-access=ALL-UNNAMED`
- JPMS use:
  - `--enable-native-access=io.github.stefan.gdal.ffm`

If native access is missing, startup fails early with an explicit message.

## Native classifier selection

`NativeLoader` resolves runtime classifier from:

- `os.name`
- `os.arch`

Mapped classifiers:

- `linux-x86_64`
- `linux-aarch64`
- `osx-x86_64`
- `osx-aarch64`
- `windows-x86_64`

If no matching bundle exists on classpath, initialization fails with a dependency hint.

## Runtime config options

After native extraction/loading, the runtime sets:

- `GDAL_DATA`
- `PROJ_LIB`
- `GDAL_DRIVER_PATH` (if present)

Values point to extracted bundle directories in `java.io.tmpdir`.

## Bundled source packages

`tools/natives/binaries.lock` is split into:

- pinned roots (`platform.<classifier>.url` + `platform.<classifier>.closure_roots`)
- resolver-managed `extra_url_<n>` entries (full transitive runtime closure)

Regenerate closure deterministically:

- `tools/natives/refresh-lock-closure.sh`
- `tools/natives/refresh-lock-closure.sh --check`
- `tools/natives/refresh-lock-closure.sh --max-backtracks 5000`
- `tools/natives/refresh-lock-closure.sh --debug-resolver`
- `GDAL_FFM_VERIFY_RESOLVER=true tools/natives/verify-lock.sh` (verifies index/groups and resolver drift)

Resolver defaults:

- bounded backtracking (`--max-backtracks`, default `2000`)
- deterministic candidate order (version desc, build desc, platform-subdir before noarch)
- MRV package selection (smallest domain first)

Tooling env vars:

- `GDAL_FFM_VERIFY_RESOLVER=true`: `verify-lock.sh` additionally runs `refresh-lock-closure.sh --check`
- `JEXTRACT_BIN=/path/to/jextract`: overrides the `jextract` executable used by `tools/jextract/regenerate.sh`

## Runtime-only bundle content

The staged classifier payload intentionally excludes CLI binaries.

- Linux/macOS: only shared libraries in `lib/` (`*.so*`, `*.dylib*`), optional `lib/gdalplugins`, plus `share/gdal` and `share/proj`
- Windows: only DLL runtime in `bin/` (`*.dll`), optional `bin/gdalplugins`, plus `share/gdal` and `share/proj`

Build/dev artifacts (`cmake`, `pkgconfig`, docs, man pages, completions, headers) are stripped during staging.

## Runtime dependency audit

Use `tools/natives/audit-runtime-deps.sh <classifier>` after staging.

- macOS: `otool -L`
- Linux: `readelf -d` (fallback `objdump -p`)
- Windows: `dumpbin /DEPENDENTS` (fallback `llvm-objdump -p`)

The audit fails fast with missing soname/dll hints mapped to the requiring binary.

## Troubleshooting

1. `Native access is not enabled`
   - Add `--enable-native-access=...` JVM arg.
2. `Required GDAL symbol not found`
   - Bundle content and expected GDAL version are out of sync.
3. `No bundled GDAL native resources found for classifier ...`
   - Missing `runtimeOnly` dependency for the matching `natives-<classifier>` artifact.
4. `SHA256 mismatch` while staging natives
   - Verify package URL and digest in `tools/natives/binaries.lock`.
5. `UnsatisfiedLinkError` / `Library not loaded ...`
   - Run `tools/natives/refresh-lock-closure.sh`, restage classifier, clear `$TMPDIR/gdal-ffm/<gdal-version>/<classifier>`, rerun smoke.
6. `Resolver conflict for '<classifier>'`
   - Means no candidate satisfied all aggregated constraints in current search path.
   - Re-run with `--debug-resolver` to see decision/backtrack trace and exact constraint origins.
7. `Resolver backtrack limit reached`
   - Re-run with higher `--max-backtracks` (for example `5000`) and keep `--debug-resolver` enabled for analysis.
