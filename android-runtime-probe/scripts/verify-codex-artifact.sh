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
readelf="$ndk_root/toolchains/llvm/prebuilt/$host_tag/bin/llvm-readelf"
artifact=${1:-"$probe_dir/app/src/main/jniLibs/$CODEX_ANDROID_ABI/libcodex_app_server.so"}

if [[ ! -x "$readelf" ]]; then
    printf 'Pinned NDK readelf is missing: %s\n' "$readelf" >&2
    exit 2
fi
if [[ ! -f "$artifact" ]]; then
    printf 'Codex artifact is missing: %s\n' "$artifact" >&2
    exit 2
fi

header=$($readelf -h "$artifact")
if ! grep -Eq 'Machine:[[:space:]]+AArch64' <<<"$header"; then
    printf '%s\n' 'Codex artifact is not AArch64.' >&2
    exit 1
fi
elf_type=$(sed -n 's/.*Type:[[:space:]]*\([A-Z]*\).*/\1/p' <<<"$header" | head -n 1)
if [[ "$elf_type" != DYN && "$elf_type" != EXEC ]]; then
    printf 'Codex artifact must be PIE ET_DYN or static ET_EXEC, got %s.\n' "$elf_type" >&2
    exit 1
fi

program_headers=$($readelf -lW "$artifact")
if [[ "$elf_type" == DYN ]]; then
    if ! grep -Fq '[Requesting program interpreter: /system/bin/linker64]' <<<"$program_headers"; then
        printf '%s\n' 'Dynamic Codex artifact does not request Android linker64.' >&2
        exit 1
    fi
elif grep -Fq ' INTERP ' <<<"$program_headers"; then
    printf '%s\n' 'Static Codex ET_EXEC unexpectedly contains an interpreter.' >&2
    exit 1
fi
if ! awk '
    $1 == "LOAD" {
        align = $NF
        sub(/^0x/, "", align)
        if (("0x" align) + 0 < 16384) bad = 1
    }
    END { exit bad ? 1 : 0 }
' <<<"$program_headers"; then
    printf '%s\n' 'Codex artifact has a PT_LOAD segment aligned below 16 KiB.' >&2
    exit 1
fi

dynamic=$($readelf -dW "$artifact")
if grep -Eq '\((RPATH|RUNPATH)\)' <<<"$dynamic"; then
    printf '%s\n' 'Codex artifact contains an RPATH/RUNPATH.' >&2
    exit 1
fi

unexpected=$(
    sed -n 's/.*Shared library: \[\([^]]*\)\].*/\1/p' <<<"$dynamic" \
        | grep -Ev '^(libc|libdl|liblog|libm)\.so$' || true
)
if [[ -n "$unexpected" ]]; then
    printf 'Unexpected dynamic dependencies:\n%s\n' "$unexpected" >&2
    exit 1
fi

printf '%s\n' "$header" | sed -n '/Class:/p;/Type:/p;/Machine:/p'
if [[ "$elf_type" == DYN ]]; then
    printf 'Interpreter: /system/bin/linker64\n'
else
    printf 'Interpreter: none (static ET_EXEC)\n'
fi
printf 'PT_LOAD alignment: >= 16384 bytes\n'
printf 'Dynamic dependencies:\n'
sed -n 's/.*Shared library: \[\([^]]*\)\].*/  \1/p' <<<"$dynamic"
shasum -a 256 "$artifact"
