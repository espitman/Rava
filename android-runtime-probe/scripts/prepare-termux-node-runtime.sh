#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROBE_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

# shellcheck source=../versions.env
. "$PROBE_DIR/versions.env"

SDK_ROOT=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [ -z "$SDK_ROOT" ]; then
  printf '%s\n' 'Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK.' >&2
  exit 1
fi

case "$(uname -s)" in
  Darwin) HOST_TAG=darwin-x86_64 ;;
  Linux) HOST_TAG=linux-x86_64 ;;
  *) printf '%s\n' 'Unsupported build host.' >&2; exit 1 ;;
esac
READELF="$SDK_ROOT/ndk/$ANDROID_NDK_VERSION/toolchains/llvm/prebuilt/$HOST_TAG/bin/llvm-readelf"
if [ ! -x "$READELF" ]; then
  printf 'Pinned NDK readelf is missing: %s\n' "$READELF" >&2
  exit 1
fi

PACKAGE_LIST="$PROBE_DIR/termux-runtime-packages.tsv"
DOWNLOAD_DIR=${RAVA_DOWNLOAD_DIR:-"$PROBE_DIR/downloads"}/termux-aarch64
JNI_DIR="$PROBE_DIR/app/src/main/jniLibs/arm64-v8a"
LICENSE_DIR="$PROBE_DIR/app/src/main/assets/termux-node-licenses"
STAGING=$(mktemp -d "${TMPDIR:-/tmp}/rava-termux-node.XXXXXX")
cleanup_staging() {
  if [ -d "$STAGING" ]; then
    find "$STAGING" -depth -delete
  fi
}
trap cleanup_staging EXIT HUP INT TERM
mkdir -p "$DOWNLOAD_DIR"
mkdir -p "$JNI_DIR"
for generated_file in \
  libnode_runtime.so libc++_shared.so libcares.so libsqlite3.so libz1.so \
  libcrypto3.so libssl3.so libicui18n78.so libicuuc78.so libicudata78.so \
  libz.so.1; do
  if [ -f "$JNI_DIR/$generated_file" ]; then
    find "$JNI_DIR/$generated_file" -delete
  fi
done
if [ -d "$LICENSE_DIR" ]; then
  find "$LICENSE_DIR" -depth -delete
fi
mkdir -p "$LICENSE_DIR"

extract_package() {
  package=$1
  archive=$2
  package_dir="$STAGING/$package"
  mkdir -p "$package_dir/ar" "$package_dir/root"
  if command -v bsdtar >/dev/null 2>&1; then
    bsdtar -xf "$archive" -C "$package_dir/ar"
  else
    (cd "$package_dir/ar" && ar -x "$archive")
  fi
  data_archive=$(find "$package_dir/ar" -maxdepth 1 -name 'data.tar.*' -print | head -n 1)
  if [ -z "$data_archive" ]; then
    printf 'No data archive in %s\n' "$archive" >&2
    exit 1
  fi
  tar -xf "$data_archive" -C "$package_dir/root"
}

while IFS='|' read -r package version size sha256 repository_path; do
  case "$package" in ''|'#'*) continue ;; esac
  archive="$DOWNLOAD_DIR/$package.deb"
  if [ ! -f "$archive" ]; then
    curl --fail --location --retry 3 \
      "$TERMUX_REPOSITORY_BASE_URL/$repository_path" --output "$archive"
  fi
  actual_size=$(wc -c < "$archive" | tr -d ' ')
  if [ "$actual_size" != "$size" ]; then
    printf '%s size mismatch: expected %s, found %s\n' "$package" "$size" "$actual_size" >&2
    exit 1
  fi
  printf '%s  %s\n' "$sha256" "$archive" | shasum -a 256 -c -
  extract_package "$package" "$archive"
  printf 'Verified Termux %s %s\n' "$package" "$version"
done < "$PACKAGE_LIST"

# Termux libicu 78.3 accidentally packaged the 14-byte body of a 404 response:
# its recipe converts the dotted release tag to a nonexistent hyphenated ref.
# Fetch the exact upstream tag's license and verify it independently.
ICU_LICENSE="$DOWNLOAD_DIR/ICU-LICENSE.txt"
if [ ! -f "$ICU_LICENSE" ]; then
  curl --fail --location --retry 3 "$ICU_LICENSE_URL" --output "$ICU_LICENSE"
fi
printf '%s  %s\n' "$ICU_LICENSE_SHA256" "$ICU_LICENSE" | shasum -a 256 -c -

PREFIX=data/data/com.termux/files/usr
install -m 755 "$STAGING/nodejs-lts/root/$PREFIX/bin/node" "$JNI_DIR/libnode_runtime.so"
install -m 644 "$STAGING/libc++/root/$PREFIX/lib/libc++_shared.so" "$JNI_DIR/libc++_shared.so"
install -m 644 "$STAGING/c-ares/root/$PREFIX/lib/libcares.so" "$JNI_DIR/libcares.so"
install -m 644 "$STAGING/libsqlite/root/$PREFIX/lib/libsqlite3.so.3.53.4" "$JNI_DIR/libsqlite3.so"
install -m 644 "$STAGING/zlib/root/$PREFIX/lib/libz.so.1.3.2" "$JNI_DIR/libz1.so"
install -m 644 "$STAGING/openssl/root/$PREFIX/lib/libcrypto.so.3" "$JNI_DIR/libcrypto3.so"
install -m 644 "$STAGING/openssl/root/$PREFIX/lib/libssl.so.3" "$JNI_DIR/libssl3.so"
install -m 644 "$STAGING/libicu/root/$PREFIX/lib/libicui18n.so.78.3" "$JNI_DIR/libicui18n78.so"
install -m 644 "$STAGING/libicu/root/$PREFIX/lib/libicuuc.so.78.3" "$JNI_DIR/libicuuc78.so"
install -m 644 "$STAGING/libicu/root/$PREFIX/lib/libicudata.so.78.3" "$JNI_DIR/libicudata78.so"

set -- \
  '--replace' 'libz.so.1=libz1.so' \
  '--replace' 'libcrypto.so.3=libcrypto3.so' \
  '--replace' 'libssl.so.3=libssl3.so' \
  '--replace' 'libicui18n.so.78=libicui18n78.so' \
  '--replace' 'libicuuc.so.78=libicuuc78.so' \
  '--replace' 'libicudata.so.78=libicudata78.so' \
  '--replace' '/data/data/com.termux/files/usr/lib:/data/data/com.termux/files/usr/lib=$ORIGIN' \
  '--replace' '/data/data/com.termux/files/usr/lib=$ORIGIN'
for elf in "$JNI_DIR"/*.so; do
  python3 "$SCRIPT_DIR/patch-elf-dynamic.py" "$elf" "$@"
done

python3 "$SCRIPT_DIR/patch-fixed-string.py" "$JNI_DIR/libcares.so" \
  '/data/data/com.termux/files/usr/etc/resolv.conf' 'resolv.conf' --count 1 --nul-pad
python3 "$SCRIPT_DIR/patch-fixed-string.py" "$JNI_DIR/libcares.so" \
  '/data/data/com.termux/files/usr/etc/hosts' '/system/etc/hosts' --count 1 --nul-pad
NODE_SHELL=$(python3 -c "print('/system/bin/' + '/' * 24 + 'sh', end='')")
NODE_BASH=$(python3 -c "print('/system/bin/' + '/' * 26 + 'sh', end='')")
python3 "$SCRIPT_DIR/patch-fixed-string.py" "$JNI_DIR/libnode_runtime.so" \
  '/data/data/com.termux/files/usr/bin/sh' "$NODE_SHELL" --count 1
python3 "$SCRIPT_DIR/patch-fixed-string.py" "$JNI_DIR/libnode_runtime.so" \
  '/data/data/com.termux/files/usr/bin/bash' "$NODE_BASH" --count 1

install -m 644 "$STAGING/nodejs-lts/root/$PREFIX/share/doc/nodejs-lts/copyright" "$LICENSE_DIR/Node-copyright.txt"
install -m 644 "$STAGING/c-ares/root/$PREFIX/share/doc/c-ares/copyright" "$LICENSE_DIR/c-ares-copyright.txt"
install -m 644 "$STAGING/zlib/root/$PREFIX/share/doc/zlib/copyright" "$LICENSE_DIR/zlib-copyright.txt"
install -m 644 "$ICU_LICENSE" "$LICENSE_DIR/ICU-LICENSE.txt"
install -m 644 "$STAGING/termux-licenses/root/$PREFIX/share/LICENSES/NCSA.txt" "$LICENSE_DIR/libcxx-NCSA.txt"
install -m 644 "$STAGING/termux-licenses/root/$PREFIX/share/LICENSES/Apache-2.0.txt" "$LICENSE_DIR/OpenSSL-Apache-2.0.txt"
install -m 644 "$STAGING/termux-licenses/root/$PREFIX/share/LICENSES/Public Domain.txt" "$LICENSE_DIR/SQLite-Public-Domain.txt"
install -m 644 "$PACKAGE_LIST" "$LICENSE_DIR/termux-package-manifest.tsv"

SYSTEM_LIBS='libc.so libm.so libdl.so liblog.so libandroid.so'
for elf in "$JNI_DIR"/*.so; do
  "$READELF" -h "$elf" | grep -q 'Machine:.*AArch64'
  "$READELF" -lW "$elf" | awk '
    $1 == "LOAD" {
      align = $NF; sub(/^0x/, "", align); value = ("0x" align) + 0
      if (value < 16384) bad = 1
    }
    END { if (bad) exit 1 }
  '
  if "$READELF" -dW "$elf" | grep -F '/data/data/com.termux/files/usr/lib' >/dev/null; then
    printf 'Unpatched Termux RUNPATH in %s\n' "$elf" >&2
    exit 1
  fi
  "$READELF" -dW "$elf" | sed -n 's/.*Shared library: \[\([^]]*\)\].*/\1/p' |
  while IFS= read -r needed; do
    case " $SYSTEM_LIBS " in
      *" $needed "*) ;;
      *)
        if [ ! -f "$JNI_DIR/$needed" ]; then
          printf '%s needs unpackaged library %s\n' "$elf" "$needed" >&2
          exit 1
        fi
        ;;
    esac
  done
done

if strings "$JNI_DIR/libcares.so" | grep -F '/data/data/com.termux/files/usr/etc/' >/dev/null; then
  printf '%s\n' 'c-ares still contains a Termux etc path.' >&2
  exit 1
fi

shasum -a 256 "$JNI_DIR"/*.so
du -sh "$JNI_DIR" "$LICENSE_DIR"
if [ "${RAVA_KEEP_DOWNLOADS:-0}" != 1 ]; then
  find "$DOWNLOAD_DIR" -depth -delete
fi
printf 'Prepared official Termux Node %s and dependency closure for APK packaging.\n' \
  "$TERMUX_NODE_VERSION"
