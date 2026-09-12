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

NDK_ROOT=${ANDROID_NDK_ROOT:-"$SDK_ROOT/ndk/$ANDROID_NDK_VERSION"}
case "$(uname -s)" in
  Darwin)
    HOST_TAG=darwin-x86_64
    HOST_CC=$(xcrun --find clang)
    HOST_CXX=$(xcrun --find clang++)
    HOST_AR=$(xcrun --find ar)
    ;;
  Linux)
    HOST_TAG=linux-x86_64
    HOST_CC=${CC_HOST:-cc}
    HOST_CXX=${CXX_HOST:-c++}
    HOST_AR=${AR_HOST:-ar}
    ;;
  *) printf '%s\n' 'Unsupported build host.' >&2; exit 1 ;;
esac

TOOLCHAIN_BIN="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG/bin"
if [ ! -x "$TOOLCHAIN_BIN/llvm-readelf" ]; then
  printf 'Pinned Android NDK is missing or incomplete: %s\n' "$NDK_ROOT" >&2
  exit 1
fi

WORK_ROOT=${RAVA_NODE_WORK_ROOT:-"$PROBE_DIR/work/node-$NODE_VERSION"}
DOWNLOAD_DIR=${RAVA_DOWNLOAD_DIR:-"$PROBE_DIR/downloads"}
ARCHIVE="$DOWNLOAD_DIR/node-v$NODE_VERSION.tar.xz"
SOURCE_DIR="$WORK_ROOT/node-v$NODE_VERSION"
OUTPUT_DIR="$PROBE_DIR/app/src/main/jniLibs/$ANDROID_ABI"
OUTPUT="$OUTPUT_DIR/libnode_runtime.so"

mkdir -p "$DOWNLOAD_DIR" "$WORK_ROOT" "$OUTPUT_DIR"

if [ ! -x "$SOURCE_DIR/android-configure" ]; then
  if [ ! -f "$ARCHIVE" ]; then
    curl --fail --location --retry 3 \
      "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION.tar.xz" \
      --output "$ARCHIVE"
  fi
  printf '%s  %s\n' "$NODE_SOURCE_SHA256" "$ARCHIVE" | shasum -a 256 -c -
  tar -xJf "$ARCHIVE" -C "$WORK_ROOT"
fi

# Re-run configuration so the generated Makefiles carry android_ndk_path even
# after an interrupted build or `make clean` removed out/Makefile.
(
  cd "$SOURCE_DIR"
  ./android-configure "$NDK_ROOT" "$ANDROID_API" "$NODE_TARGET_ARCH"
)

BUILD_JOBS=${RAVA_BUILD_JOBS:-4}
BUILD_LOG="$WORK_ROOT/build.log"
if ! (
  cd "$SOURCE_DIR"
  # android-configure does not select the target archiver or native host
  # compilers on every host. Override both toolsets explicitly.
  make -j"$BUILD_JOBS" node \
    "AR.target=$TOOLCHAIN_BIN/llvm-ar" \
    "CC.host=$HOST_CC" \
    "CXX.host=$HOST_CXX" \
    "LINK.host=$HOST_CXX" \
    "AR.host=$HOST_AR" \
    'LDFLAGS.target=-Wl,-z,max-page-size=16384' \
    > "$BUILD_LOG" 2>&1
); then
  printf 'Node Android build failed. Last build output from %s:\n' "$BUILD_LOG" >&2
  tail -n 80 "$BUILD_LOG" >&2
  exit 1
fi

NODE_BINARY="$SOURCE_DIR/out/Release/node"
if [ ! -x "$NODE_BINARY" ]; then
  printf 'Node build completed without the expected executable: %s\n' "$NODE_BINARY" >&2
  exit 1
fi

install -m 755 "$NODE_BINARY" "$OUTPUT"

READELF="$TOOLCHAIN_BIN/llvm-readelf"
"$READELF" -h "$OUTPUT" | sed -n '/Class:/p;/Machine:/p;/Type:/p'
NEEDED_LIBRARIES=$("$READELF" -dW "$OUTPUT" | sed -n '/NEEDED/p')
printf '%s\n' "$NEEDED_LIBRARIES"

case "$NEEDED_LIBRARIES" in
  *libc++_shared.so*)
    LIBCXX_SOURCE="$TOOLCHAIN_BIN/../sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
    if [ ! -f "$LIBCXX_SOURCE" ]; then
      printf 'Node needs libc++_shared.so but it is missing from the pinned NDK: %s\n' \
        "$LIBCXX_SOURCE" >&2
      exit 1
    fi
    install -m 644 "$LIBCXX_SOURCE" "$OUTPUT_DIR/libc++_shared.so"
    ;;
esac

"$READELF" -lW "$OUTPUT" | awk '
  $1 == "LOAD" {
    align = $NF
    sub(/^0x/, "", align)
    value = ("0x" align) + 0
    if (value < 16384) bad = 1
  }
  END {
    if (bad) {
      print "ELF has a PT_LOAD segment aligned below 16 KiB." > "/dev/stderr"
      exit 1
    }
  }
'

shasum -a 256 "$OUTPUT"
if [ "${RAVA_KEEP_DOWNLOADS:-0}" != 1 ] && [ -f "$ARCHIVE" ]; then
  find "$ARCHIVE" -delete
fi
printf 'Packaged Node v%s for %s at %s\n' "$NODE_VERSION" "$ANDROID_ABI" "$OUTPUT"
