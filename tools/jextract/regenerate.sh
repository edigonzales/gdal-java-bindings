#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CORE_DIR="$ROOT_DIR/gdal-ffm-core"
HEADER_FILE="$CORE_DIR/src/main/native/gdal_ffi.h"
OUTPUT_DIR="$CORE_DIR/src/generated/java"
PACKAGE_NAME="ch.so.agi.gdal.ffm.generated"
CLASS_NAME="GdalGenerated"
JEXTRACT_BIN="${JEXTRACT_BIN:-jextract}"

if [[ -z "${GDAL_INCLUDE_DIR:-}" ]]; then
  echo "GDAL_INCLUDE_DIR is required (path containing gdal.h, gdal_utils.h, ogr_api.h, ogr_srs_api.h, cpl_error.h, cpl_conv.h)" >&2
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
  --include-function "GDALWarpAppOptionsNew" \
  --include-function "GDALWarpAppOptionsFree" \
  --include-function "GDALWarpAppOptionsSetProgress" \
  --include-function "GDALWarp" \
  --include-function "GDALTranslateOptionsNew" \
  --include-function "GDALTranslateOptionsFree" \
  --include-function "GDALTranslateOptionsSetProgress" \
  --include-function "GDALTranslate" \
  --include-function "CPLErrorReset" \
  --include-function "CPLGetLastErrorType" \
  --include-function "CPLGetLastErrorNo" \
  --include-function "CPLGetLastErrorMsg" \
  --include-function "CPLFree" \
  --include-function "VSIFree" \
  --include-function "CPLSetConfigOption" \
  -I "$GDAL_INCLUDE_DIR" \
  "$HEADER_FILE"

cat <<MSG
Regenerated FFM bindings in:
  $OUTPUT_DIR

Review generated files and commit them together with any wrapper adjustments.
MSG
