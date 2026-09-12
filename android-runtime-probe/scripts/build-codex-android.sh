#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
probe_dir=$(cd -- "$script_dir/.." && pwd)

# shellcheck source=../codex-versions.env
source "$probe_dir/codex-versions.env"

sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [[ -z "$sdk_root" ]]; then
    printf '%s\n' 'Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK.' >&2
    exit 2
fi

ndk_root=${ANDROID_NDK_ROOT:-"$sdk_root/ndk/$CODEX_ANDROID_NDK_VERSION"}
case "$(uname -s)" in
    Darwin) host_tag=darwin-x86_64 ;;
    Linux) host_tag=linux-x86_64 ;;
    *) printf 'Unsupported NDK host: %s\n' "$(uname -s)" >&2; exit 2 ;;
esac
toolchain="$ndk_root/toolchains/llvm/prebuilt/$host_tag/bin"
linker="$toolchain/aarch64-linux-android${CODEX_ANDROID_API}-clang"

for required in git rustup cargo "$linker" "$toolchain/llvm-ar" "$toolchain/llvm-strip"; do
    if ! command -v "$required" >/dev/null 2>&1 && [[ ! -x "$required" ]]; then
        printf 'Required build tool is missing: %s\n' "$required" >&2
        exit 2
    fi
done

# A clean release build of the large Codex workspace needs substantial scratch
# space. Refuse early rather than filling the workstation. Override only when a
# measured build cache makes a lower threshold safe.
minimum_free_kib=${RAVA_CODEX_MIN_FREE_KIB:-12582912}
available_kib=$(df -Pk "$probe_dir" | awk 'NR == 2 { print $4 }')
if (( available_kib < minimum_free_kib )); then
    printf 'Insufficient free disk for Codex build: %s KiB available; %s KiB required.\n' \
        "$available_kib" "$minimum_free_kib" >&2
    exit 3
fi

work_root=${RAVA_CODEX_WORK_ROOT:-"$probe_dir/work/codex-$CODEX_VERSION"}
source_dir="$work_root/source"
target_dir=${RAVA_CODEX_TARGET_DIR:-"$work_root/target"}
destination_dir="$probe_dir/app/src/main/jniLibs/$CODEX_ANDROID_ABI"
destination="$destination_dir/libcodex_app_server.so"
patch_dir="$probe_dir/patches/codex-$CODEX_VERSION"

mkdir -p "$work_root" "$destination_dir"
if [[ ! -d "$source_dir/.git" ]]; then
    git init "$source_dir"
    git -C "$source_dir" remote add origin https://github.com/openai/codex.git
fi

if ! git -C "$source_dir" cat-file -e "$CODEX_GIT_COMMIT^{commit}" 2>/dev/null; then
    git -C "$source_dir" fetch --depth 1 origin "refs/tags/$CODEX_GIT_TAG"
fi
resolved_commit=$(git -C "$source_dir" rev-parse "$CODEX_GIT_COMMIT^{commit}")
if [[ "$resolved_commit" != "$CODEX_GIT_COMMIT" ]]; then
    printf 'Pinned Codex commit mismatch: expected %s, resolved %s\n' \
        "$CODEX_GIT_COMMIT" "$resolved_commit" >&2
    exit 1
fi
if [[ "$(git -C "$source_dir" rev-parse HEAD 2>/dev/null || true)" != "$CODEX_GIT_COMMIT" ]]; then
    if [[ -n "$(git -C "$source_dir" status --porcelain 2>/dev/null || true)" ]]; then
        printf '%s\n' 'Refusing to switch a modified Codex work checkout.' >&2
        exit 1
    fi
    git -C "$source_dir" checkout --detach "$CODEX_GIT_COMMIT"
fi

if [[ -d "$patch_dir" ]]; then
    find "$patch_dir" -maxdepth 1 -type f -name '*.patch' -print0 \
        | LC_ALL=C sort -z \
        | while IFS= read -r -d '' patch_file; do
        printf 'Applying maintained Android patch: %s\n' "${patch_file#$probe_dir/}"
        if git -C "$source_dir" apply --reverse --check "$patch_file" 2>/dev/null; then
            printf '%s\n' 'Patch is already applied.'
        else
            git -C "$source_dir" apply --check "$patch_file"
            git -C "$source_dir" apply "$patch_file"
        fi
    done
fi

rustup toolchain install "$CODEX_RUST_TOOLCHAIN" --profile minimal
rustup target add --toolchain "$CODEX_RUST_TOOLCHAIN" "$CODEX_RUST_TARGET"

export CC_aarch64_linux_android="$linker"
export CXX_aarch64_linux_android="$toolchain/aarch64-linux-android${CODEX_ANDROID_API}-clang++"
export AR_aarch64_linux_android="$toolchain/llvm-ar"
export RANLIB_aarch64_linux_android="$toolchain/llvm-ranlib"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$linker"
export CARGO_TARGET_DIR="$target_dir"
export RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384'
export CARGO_BUILD_JOBS=${RAVA_CODEX_BUILD_JOBS:-2}

cargo "+$CODEX_RUST_TOOLCHAIN" build \
    --locked \
    --manifest-path "$source_dir/codex-rs/Cargo.toml" \
    --target "$CODEX_RUST_TARGET" \
    --release \
    --package codex-app-server \
    --bin codex-app-server

built_binary="$target_dir/$CODEX_RUST_TARGET/release/codex-app-server"
if [[ ! -f "$built_binary" ]]; then
    printf 'Cargo completed without the expected binary: %s\n' "$built_binary" >&2
    exit 1
fi

"$toolchain/llvm-strip" --strip-unneeded "$built_binary" -o "$destination"
chmod 0755 "$destination"
"$script_dir/verify-codex-artifact.sh" "$destination"
printf 'Packaged Codex app-server %s for %s at %s\n' \
    "$CODEX_VERSION" "$CODEX_ANDROID_ABI" "$destination"
