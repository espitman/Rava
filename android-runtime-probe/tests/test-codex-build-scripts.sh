#!/usr/bin/env bash
set -euo pipefail

test_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
probe_dir=$(cd -- "$test_dir/.." && pwd)
build_script="$probe_dir/scripts/build-codex-android.sh"
verify_script="$probe_dir/scripts/verify-codex-artifact.sh"
fetch_script="$probe_dir/scripts/fetch-codex-release.sh"

# shellcheck source=../codex-versions.env
source "$probe_dir/codex-versions.env"

[[ "$CODEX_GIT_COMMIT" =~ ^[0-9a-f]{40}$ ]]
[[ "$CODEX_RELEASE_ARCHIVE_SHA256" =~ ^[0-9a-f]{64}$ ]]
[[ "$CODEX_RELEASE_BINARY_SHA256" =~ ^[0-9a-f]{64}$ ]]
[[ "$CODEX_ANDROID_PATCHED_SHA256" =~ ^[0-9a-f]{64}$ ]]
[[ "$CODEX_RELEASE_SIGSTORE_SHA256" =~ ^[0-9a-f]{64}$ ]]
[[ "$CODEX_RUST_TARGET" == aarch64-linux-android ]]
[[ "$CODEX_ANDROID_ABI" == arm64-v8a ]]
(( CODEX_ANDROID_API >= 26 ))
[[ "$CODEX_RELEASE_CERT_IDENTITY" == *"/openai/codex/.github/workflows/rust-release.yml@refs/tags/$CODEX_GIT_TAG" ]]
[[ "$CODEX_RELEASE_OIDC_ISSUER" == https://token.actions.githubusercontent.com ]]

bash -n "$build_script" "$verify_script" "$fetch_script"
sh -n "$build_script" "$verify_script" "$fetch_script"

grep -Fq 'cosign verify-blob' "$fetch_script"
grep -Fq 'CODEX_RELEASE_BINARY_SHA256' "$fetch_script"
grep -Fq "'/etc/resolv.conf' 'resolv.conf' --count 2 --nul-pad" "$fetch_script"
grep -Fq 'CODEX_ANDROID_PATCHED_SHA256' "$fetch_script"

patch_fixture=$(mktemp "${TMPDIR:-/tmp}/rava-codex-resolver.XXXXXX")
printf 'a/etc/resolv.confb/etc/resolv.confc' >"$patch_fixture"
python3 "$probe_dir/scripts/patch-fixed-string.py" "$patch_fixture" \
    '/etc/resolv.conf' 'resolv.conf' --count 2 --nul-pad >/dev/null
python3 - "$patch_fixture" <<'PY'
from pathlib import Path
import sys

data = Path(sys.argv[1]).read_bytes()
assert data == b"aresolv.conf\0\0\0\0\0bresolv.conf\0\0\0\0\0c"
PY

preflight_output=$(mktemp "${TMPDIR:-/tmp}/rava-codex-preflight.XXXXXX")
verify_output=$(mktemp "${TMPDIR:-/tmp}/rava-codex-verify.XXXXXX")
trap 'rm -f "$preflight_output" "$verify_output" "$patch_fixture"' EXIT

set +e
RAVA_CODEX_MIN_FREE_KIB=999999999999 "$build_script" >"$preflight_output" 2>&1
result_code=$?
set -e
if (( result_code != 3 )); then
    printf 'Expected disk preflight exit 3, got %s\n' "$result_code" >&2
    sed -n '1,40p' "$preflight_output" >&2
    exit 1
fi
grep -Fq 'Insufficient free disk for Codex build' "$preflight_output"

missing_artifact="$probe_dir/app/src/main/jniLibs/$CODEX_ANDROID_ABI/not-present.so"
set +e
"$verify_script" "$missing_artifact" >"$verify_output" 2>&1
result_code=$?
set -e
if (( result_code != 2 )); then
    printf 'Expected missing-artifact exit 2, got %s\n' "$result_code" >&2
    sed -n '1,40p' "$verify_output" >&2
    exit 1
fi
grep -Fq "Codex artifact is missing: $missing_artifact" "$verify_output"

printf '%s\n' 'Codex Android build-script checks passed.'
