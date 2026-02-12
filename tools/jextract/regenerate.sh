#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CORE_DIR="$ROOT_DIR/gdal-ffm-core"
HEADER_FILE="$CORE_DIR/src/main/native/gdal_ffi.h"
OUTPUT_DIR="$CORE_DIR/src/generated/java"
PACKAGE_NAME="io.github.stefan.gdal.ffm.generated"
CLASS_NAME="GdalGenerated"
JEXTRACT_BIN="${JEXTRACT_BIN:-jextract}"

if [[ -z "${GDAL_INCLUDE_DIR:-}" ]]; then
  echo "GDAL_INCLUDE_DIR is required (path containing gdal.h, gdal_utils.h, cpl_error.h, cpl_conv.h)" >&2
  exit 1
fi

if ! command -v "$JEXTRACT_BIN" >/dev/null 2>&1; then
  echo "jextract executable not found: $JEXTRACT_BIN" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

"$JEXTRACT_BIN" \
  --target-package "$PACKAGE_NAME" \
  --header-class-name "$CLASS_NAME" \
  --output "$OUTPUT_DIR" \
  --include-function "GDAL(AllRegister|OpenEx|Close|ReleaseDataset)" \
  --include-function "GDAL(VectorTranslate.*|Warp.*|Translate.*)" \
  --include-function "CPL(ErrorReset|GetLastError(Type|No|Msg)|Free|SetConfigOption)" \
  --include-constant "GDAL_OF_.*" \
  --include-constant "CE_.*" \
  -I "$GDAL_INCLUDE_DIR" \
  "$HEADER_FILE"

cat <<MSG
Regenerated FFM bindings in:
  $OUTPUT_DIR

Review generated files and commit them together with any wrapper adjustments.
MSG
