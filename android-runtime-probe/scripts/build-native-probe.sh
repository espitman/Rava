#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
output_root=${1:?usage: build-native-probe.sh OUTPUT_DIRECTORY}
ndk_root=${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must point to the pinned Android NDK}
case "$(uname -s)" in
    Darwin) host_tag=darwin-x86_64 ;;
    Linux) host_tag=linux-x86_64 ;;
    *) printf 'Unsupported NDK host: %s\n' "$(uname -s)" >&2; exit 2 ;;
esac
toolchain="$ndk_root/toolchains/llvm/prebuilt/$host_tag/bin"
compiler="$toolchain/aarch64-linux-android26-clang"
stripper="$toolchain/llvm-strip"
source_file="$script_dir/../app/src/main/cpp/probe_main.c"
destination_dir="$output_root/arm64-v8a"
destination="$destination_dir/librava_probe_exec.so"

if [[ ! -x "$compiler" ]]; then
    printf 'Android NDK compiler not found: %s\n' "$compiler" >&2
    exit 2
fi

mkdir -p "$destination_dir"
"$compiler" \
    -std=c17 \
    -Os \
    -fPIE \
    -pie \
    -fstack-protector-strong \
    -D_FORTIFY_SOURCE=2 \
    -Werror \
    -Wall \
    -Wextra \
    -Wl,--build-id=sha1 \
    -Wl,-z,max-page-size=16384 \
    -Wl,-z,common-page-size=4096 \
    "$source_file" \
    -o "$destination"
"$stripper" --strip-unneeded "$destination"
chmod 0755 "$destination"
