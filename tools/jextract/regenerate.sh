#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CORE_DIR="$ROOT_DIR/gdal-ffm-core"
HEADER_FILE="$CORE_DIR/src/main/native/gdal_ffi.h"
OUTPUT_DIR="$CORE_DIR/src/generated/java"
PACKAGE_NAME="ch.so.agi.gdal.ffm.generated"
CLASS_NAME="GdalGenerated"
JEXTRACT_BIN="${JEXTRACT_BIN:-jextract}"
EXPECTED_JEXTRACT_VERSION="${JEXTRACT_EXPECTED_VERSION:-jextract 22}"
EXPECTED_GDAL_VERSION="$(sed -n 's/^gdalVersion=//p' "$ROOT_DIR/gradle.properties")"

if [[ -z "${GDAL_INCLUDE_DIR:-}" ]]; then
  echo "GDAL_INCLUDE_DIR is required (path containing GDAL headers)" >&2
  exit 1
fi

if ! command -v "$JEXTRACT_BIN" >/dev/null 2>&1; then
  echo "jextract executable not found: $JEXTRACT_BIN" >&2
  exit 1
fi

actual_jextract_version="$($JEXTRACT_BIN --version 2>&1 | head -n 1)"
if [[ "$actual_jextract_version" != "$EXPECTED_JEXTRACT_VERSION" ]]; then
  echo "Unexpected jextract version: $actual_jextract_version" >&2
  echo "Expected: $EXPECTED_JEXTRACT_VERSION" >&2
  exit 1
fi

version_header="$GDAL_INCLUDE_DIR/gdal_version.h"
if [[ ! -f "$version_header" ]]; then
  echo "Missing GDAL version header: $version_header" >&2
  exit 1
fi
read_define() {
  local name="$1"
  sed -nE "s/^[[:space:]]*#[[:space:]]*define[[:space:]]+$name[[:space:]]+([0-9]+).*/\\1/p" "$version_header" | head -n 1
}
major="$(read_define GDAL_VERSION_MAJOR)"
minor="$(read_define GDAL_VERSION_MINOR)"
revision="$(read_define GDAL_VERSION_REV)"
actual_gdal_version="$major.$minor.$revision"
if [[ "$actual_gdal_version" != "$EXPECTED_GDAL_VERSION" ]]; then
  echo "GDAL header version mismatch: found $actual_gdal_version, expected $EXPECTED_GDAL_VERSION" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

"$JEXTRACT_BIN" \
  --target-package "$PACKAGE_NAME" \
  --header-class-name "$CLASS_NAME" \
  --output "$OUTPUT_DIR" \
  --include-function "GDALAllRegister" \
  --include-function "GDALOpenEx" \
  --include-function "GDALClose" \
  --include-function "GDALGetDriverCount" \
  --include-function "GDALGetDriver" \
  --include-function "GDALGetDriverByName" \
  --include-function "GDALGetDriverShortName" \
  --include-function "GDALGetDriverLongName" \
  --include-function "GDALGetMetadataItem" \
  --include-function "GDALReleaseDataset" \
  --include-function "GDALDatasetGetLayerByName" \
  --include-function "GDALDatasetGetLayer" \
  --include-function "GDALDatasetGetLayerCount" \
  --include-function "OGRGetDriverCount" \
  --include-function "OGRGetDriver" \
  --include-function "OGRGetDriverByName" \
  --include-function "OGR_Dr_GetName" \
  --include-function "OGR_Dr_TestCapability" \
  --include-function "OGR_Dr_CreateDataSource" \
  --include-function "OGR_Dr_DeleteDataSource" \
  --include-function "OGR_DS_CreateLayer" \
  --include-function "OGR_DS_DeleteLayer" \
  --include-function "OGR_L_GetName" \
  --include-function "OGR_L_GetGeomType" \
  --include-function "OGR_L_GetLayerDefn" \
  --include-function "OGR_L_GetNextFeature" \
  --include-function "OGR_L_ResetReading" \
  --include-function "OGR_L_SetSpatialFilter" \
  --include-function "OGR_L_SetSpatialFilterRect" \
  --include-function "OGR_L_SetAttributeFilter" \
  --include-function "OGR_L_SetIgnoredFields" \
  --include-function "OGR_L_CreateFeature" \
  --include-function "OGR_L_CreateField" \
  --include-function "OGR_F_Create" \
  --include-function "OGR_F_Destroy" \
  --include-function "OGR_F_GetFID" \
  --include-function "OGR_F_GetGeometryRef" \
  --include-function "OGR_F_SetGeometry" \
  --include-function "OGR_F_GetFieldIndex" \
  --include-function "OGR_F_IsFieldSetAndNotNull" \
  --include-function "OGR_FD_GetFieldCount" \
  --include-function "OGR_FD_GetFieldDefn" \
  --include-function "OGR_FD_GetGeomFieldIndex" \
  --include-function "OGR_Fld_Create" \
  --include-function "OGR_Fld_Destroy" \
  --include-function "OGR_Fld_GetNameRef" \
  --include-function "OGR_Fld_GetType" \
  --include-function "OGR_F_GetFieldAsString" \
  --include-function "OGR_F_GetFieldAsInteger64" \
  --include-function "OGR_F_GetFieldAsDouble" \
  --include-function "OGR_F_SetFieldString" \
  --include-function "OGR_F_SetFieldInteger64" \
  --include-function "OGR_F_SetFieldDouble" \
  --include-function "OGR_F_SetFieldNull" \
  --include-function "OGR_F_SetFID" \
  --include-function "OGR_F_SetGeomField" \
  --include-function "OGR_G_CreateFromWkt" \
  --include-function "OGR_G_ExportToWkb" \
  --include-function "OGR_G_WkbSize" \
  --include-function "OGR_G_CreateFromWkb" \
  --include-function "OGR_G_DestroyGeometry" \
  --include-function "OGR_G_GetSpatialReference" \
  --include-function "OSRGetAuthorityCode" \
  --include-function "GDALVectorTranslateOptionsNew" \
  --include-function "GDALVectorTranslateOptionsFree" \
  --include-function "GDALVectorTranslateOptionsSetProgress" \
  --include-function "GDALVectorTranslate" \
  --include-function "GDALGetGlobalAlgorithmRegistry" \
  --include-function "GDALAlgorithmRegistryRelease" \
  --include-function "GDALAlgorithmRegistryInstantiateAlgFromPath" \
  --include-function "GDALAlgorithmRelease" \
  --include-function "GDALAlgorithmParseCommandLineArguments" \
  --include-function "GDALAlgorithmGetActualAlgorithm" \
  --include-function "GDALAlgorithmRun" \
  --include-function "GDALAlgorithmFinalize" \
  --include-function "GDALAlgorithmGetArgNames" \
  --include-function "GDALAlgorithmGetArg" \
  --include-function "GDALAlgorithmArgRelease" \
  --include-function "GDALAlgorithmArgGetType" \
  --include-function "GDALAlgorithmArgIsOutput" \
  --include-function "GDALAlgorithmArgGetAsString" \
  --include-function "CSLCount" \
  --include-function "CSLDestroy" \
  --include-function "CPLErrorReset" \
  --include-function "CPLGetLastErrorType" \
  --include-function "CPLGetLastErrorNo" \
  --include-function "CPLGetLastErrorMsg" \
  --include-function "CPLFree" \
  --include-function "VSIFree" \
  --include-function "CPLSetConfigOption" \
  --include-function "CPLSetThreadLocalConfigOption" \
  --include-function "CPLGetThreadLocalConfigOption" \
  -I "$GDAL_INCLUDE_DIR" \
  "$HEADER_FILE"

SHARED_FILE="$OUTPUT_DIR/${PACKAGE_NAME//.//}/${CLASS_NAME}\$shared.java"
if [[ -f "$SHARED_FILE" ]]; then
  # jextract 22 emits C_LONG as OfLong even on platforms where the canonical C long
  # layout is not represented by that concrete subtype. Keep this compatibility
  # rewrite deterministic until the generator is upgraded and the patch is no longer needed.
  perl -0pi -e 's/public static final ValueLayout\.OfLong C_LONG = \(ValueLayout\.OfLong\) Linker\.nativeLinker\(\)\.canonicalLayouts\(\)\.get\("long"\);/public static final ValueLayout C_LONG = (ValueLayout) Linker.nativeLinker().canonicalLayouts().get("long");/' "$SHARED_FILE"
fi

cat <<MSG
Regenerated FFM bindings in:
  $OUTPUT_DIR
using:
  jextract: $actual_jextract_version
  GDAL headers: $actual_gdal_version

Review generated files and commit them together with any wrapper adjustments.
MSG
