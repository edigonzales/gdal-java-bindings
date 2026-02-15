#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <classifier> <bundle-dir>" >&2
  exit 1
fi

CLASSIFIER="$1"
BUNDLE_DIR="$2"
TMP_ROOT="${TMPDIR:-/tmp}"
LIB_BASENAMES_FILE="$(mktemp "$TMP_ROOT/gdal-ffm-relocate-${CLASSIFIER}.XXXXXX")"
trap 'rm -f "$LIB_BASENAMES_FILE"' EXIT

if [[ ! -d "$BUNDLE_DIR" ]]; then
  echo "Bundle directory does not exist: $BUNDLE_DIR" >&2
  exit 1
fi

classifier_os() {
  case "$CLASSIFIER" in
    linux-*)
      echo "linux"
      ;;
    osx-*)
      echo "osx"
      ;;
    windows-*)
      echo "windows"
      ;;
    *)
      echo "Unsupported classifier: $CLASSIFIER" >&2
      exit 1
      ;;
  esac
}

contains_placeholder_marker() {
  local value="$1"
  if [[ "$value" == *"_h_env_placehold"* ]]; then
    return 0
  fi
  if [[ "$value" == *"/home/conda/feedstock_root"* ]]; then
    return 0
  fi
  if [[ "$value" == *"/opt/conda"* ]]; then
    return 0
  fi
  if [[ "$value" == *"C:\\b\\"* ]]; then
    return 0
  fi
  return 1
}

is_allowed_system_linux_absolute() {
  local dep="$1"
  case "$dep" in
    /lib/*|/lib64/*|/usr/lib/*|/usr/lib64/*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

collect_bundle_lib_basenames() {
  case "$OS_FAMILY" in
    linux|osx)
      : > "$LIB_BASENAMES_FILE"
      while IFS= read -r file; do
        basename "$file"
      done < <(
        {
          if [[ -d "$BUNDLE_DIR/lib" ]]; then
            find "$BUNDLE_DIR/lib" \( -type f -o -type l \) \
              \( -name '*.so' -o -name '*.so.*' -o -name '*.dylib' -o -name '*.dylib.*' \) \
              -print
          fi
          if [[ -d "$BUNDLE_DIR/lib/gdalplugins" ]]; then
            find "$BUNDLE_DIR/lib/gdalplugins" \( -type f -o -type l \) \
              \( -name '*.so' -o -name '*.so.*' -o -name '*.dylib' -o -name '*.dylib.*' \) \
              -print
          fi
        }
      ) | sort -u > "$LIB_BASENAMES_FILE"
      ;;
    windows)
      : > "$LIB_BASENAMES_FILE"
      if [[ -d "$BUNDLE_DIR/bin" ]]; then
        while IFS= read -r file; do
          basename "$file"
        done < <(find "$BUNDLE_DIR/bin" \( -type f -o -type l \) -iname '*.dll' -print) \
          | tr '[:lower:]' '[:upper:]' \
          | sort -u > "$LIB_BASENAMES_FILE"
      fi
      ;;
  esac
}

is_bundle_lib() {
  local lib_name="$1"
  grep -Fxq "$lib_name" "$LIB_BASENAMES_FILE"
}

linux_binaries() {
  {
    if [[ -d "$BUNDLE_DIR/lib" ]]; then
      find "$BUNDLE_DIR/lib" -type f \( -name '*.so' -o -name '*.so.*' \) -print
    fi
    if [[ -d "$BUNDLE_DIR/lib/gdalplugins" ]]; then
      find "$BUNDLE_DIR/lib/gdalplugins" -type f \( -name '*.so' -o -name '*.so.*' \) -print
    fi
  } | sort -u
}

macos_main_binaries() {
  if [[ -d "$BUNDLE_DIR/lib" ]]; then
    find "$BUNDLE_DIR/lib" -maxdepth 1 -type f \( -name '*.dylib' -o -name '*.dylib.*' \) -print | sort -u
  fi
}

macos_plugin_binaries() {
  if [[ -d "$BUNDLE_DIR/lib/gdalplugins" ]]; then
    find "$BUNDLE_DIR/lib/gdalplugins" -type f \( -name '*.dylib' -o -name '*.dylib.*' \) -print | sort -u
  fi
}

macos_all_binaries() {
  {
    macos_main_binaries
    macos_plugin_binaries
  } | sort -u
}

windows_binaries() {
  if [[ -d "$BUNDLE_DIR/bin" ]]; then
    find "$BUNDLE_DIR/bin" -type f -iname '*.dll' -print | sort -u
  fi
}

linux_relocate() {
  if ! command -v readelf >/dev/null 2>&1; then
    echo "Missing required tool for linux relocation: readelf" >&2
    exit 1
  fi
  if ! command -v patchelf >/dev/null 2>&1; then
    echo "Missing required tool for linux relocation: patchelf" >&2
    exit 1
  fi

  local binary dep dep_base
  while IFS= read -r binary; do
    while IFS= read -r dep; do
      [[ -z "$dep" ]] && continue

      if [[ "$dep" == */* ]]; then
        dep_base="$(basename "$dep")"
        if is_bundle_lib "$dep_base"; then
          echo "Rewriting DT_NEEDED in $binary: $dep -> $dep_base"
          patchelf --replace-needed "$dep" "$dep_base" "$binary"
          continue
        fi

        if is_allowed_system_linux_absolute "$dep"; then
          continue
        fi

        echo "Non-relocatable absolute DT_NEEDED dependency '$dep' in $binary" >&2
        exit 1
      fi
    done < <(readelf -d "$binary" | sed -n 's/.*Shared library: \[\([^]]*\)\].*/\1/p')
  done < <(linux_binaries)

  while IFS= read -r binary; do
    while IFS= read -r dep; do
      [[ -z "$dep" ]] && continue
      if contains_placeholder_marker "$dep"; then
        echo "Placeholder dependency remained after relocation: '$dep' in $binary" >&2
        exit 1
      fi
    done < <(readelf -d "$binary" | sed -n 's/.*Shared library: \[\([^]]*\)\].*/\1/p')
  done < <(linux_binaries)
}

has_macos_rpath() {
  local binary="$1"
  local wanted="$2"
  otool -l "$binary" | awk -v wanted="$wanted" '
    $1 == "cmd" && $2 == "LC_RPATH" { in_rpath = 1; next }
    in_rpath && $1 == "path" {
      if ($2 == wanted) {
        found = 1
      }
      in_rpath = 0
    }
    END {
      if (found == 1) {
        exit 0
      }
      exit 1
    }
  '
}

ensure_macos_rpath() {
  local binary="$1"
  local rpath="$2"
  if ! has_macos_rpath "$binary" "$rpath"; then
    echo "Adding LC_RPATH '$rpath' to $binary"
    install_name_tool -add_rpath "$rpath" "$binary"
  fi
}

macos_relocate() {
  if ! command -v otool >/dev/null 2>&1; then
    echo "Missing required tool for macOS relocation: otool" >&2
    exit 1
  fi
  if ! command -v install_name_tool >/dev/null 2>&1; then
    echo "Missing required tool for macOS relocation: install_name_tool" >&2
    exit 1
  fi

  local binary dep dep_base

  while IFS= read -r binary; do
    dep_base="$(basename "$binary")"
    echo "Setting install name for $binary to @rpath/$dep_base"
    install_name_tool -id "@rpath/$dep_base" "$binary"
  done < <(macos_main_binaries)

  while IFS= read -r binary; do
    while IFS= read -r dep; do
      dep="${dep%% (*}"
      dep="${dep#${dep%%[![:space:]]*}}"
      [[ -z "$dep" ]] && continue

      case "$dep" in
        /usr/lib/*|/System/Library/*)
          continue
          ;;
        @rpath/*|@loader_path/*|@executable_path/*)
          continue
          ;;
      esac

      if [[ "$dep" == /* ]]; then
        dep_base="$(basename "$dep")"
        if is_bundle_lib "$dep_base"; then
          echo "Rewriting install name in $binary: $dep -> @rpath/$dep_base"
          install_name_tool -change "$dep" "@rpath/$dep_base" "$binary"
          continue
        fi
        echo "Non-relocatable absolute install name '$dep' in $binary" >&2
        exit 1
      fi

      echo "Unsupported install name '$dep' in $binary" >&2
      exit 1
    done < <(otool -L "$binary" | tail -n +2)
  done < <(macos_all_binaries)

  while IFS= read -r binary; do
    ensure_macos_rpath "$binary" "@loader_path"
    ensure_macos_rpath "$binary" "@loader_path/gdalplugins"
  done < <(macos_main_binaries)

  while IFS= read -r binary; do
    ensure_macos_rpath "$binary" "@loader_path"
    ensure_macos_rpath "$binary" "@loader_path/.."
  done < <(macos_plugin_binaries)
}

windows_validate() {
  local binary
  while IFS= read -r binary; do
    if grep -aF -q \
      -e "_h_env_placehold" \
      -e "/home/conda/feedstock_root" \
      -e "/opt/conda" \
      -e "C:\\b\\" \
      "$binary"; then
      echo "Placeholder marker detected in Windows DLL: $binary" >&2
      exit 1
    fi
  done < <(windows_binaries)
}

OS_FAMILY="$(classifier_os)"
collect_bundle_lib_basenames

case "$OS_FAMILY" in
  linux)
    linux_relocate
    ;;
  osx)
    macos_relocate
    ;;
  windows)
    windows_validate
    ;;
esac

echo "Relocation/sanitization completed for $CLASSIFIER"
