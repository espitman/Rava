#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
probe_dir=$(cd -- "$script_dir/.." && pwd)

# shellcheck source=../codex-versions.env
source "$probe_dir/codex-versions.env"

for required in curl cosign tar shasum; do
    if ! command -v "$required" >/dev/null 2>&1; then
        printf 'Required release verification tool is missing: %s\n' "$required" >&2
        exit 2
    fi
done

download_dir=${RAVA_DOWNLOAD_DIR:-"$probe_dir/downloads"}
archive="$download_dir/$CODEX_RELEASE_ARCHIVE"
sigstore="$download_dir/$CODEX_RELEASE_SIGSTORE"
release_url="https://github.com/openai/codex/releases/download/$CODEX_GIT_TAG"
destination_dir="$probe_dir/app/src/main/jniLibs/$CODEX_ANDROID_ABI"
destination="$destination_dir/libcodex_app_server.so"
staging=$(mktemp "${TMPDIR:-/tmp}/rava-codex-binary.XXXXXX")
trap 'if [[ -f "$staging" ]]; then unlink "$staging"; fi' EXIT

mkdir -p "$download_dir" "$destination_dir"
if [[ ! -f "$archive" ]]; then
    curl --fail --location --retry 3 "$release_url/$CODEX_RELEASE_ARCHIVE" --output "$archive"
fi
if [[ ! -f "$sigstore" ]]; then
    curl --fail --location --retry 3 "$release_url/$CODEX_RELEASE_SIGSTORE" --output "$sigstore"
fi

printf '%s  %s\n' "$CODEX_RELEASE_ARCHIVE_SHA256" "$archive" | shasum -a 256 -c -
printf '%s  %s\n' "$CODEX_RELEASE_SIGSTORE_SHA256" "$sigstore" | shasum -a 256 -c -

archive_entries=$(tar -tzf "$archive")
if [[ "$archive_entries" != "$CODEX_RELEASE_BINARY" ]]; then
    printf 'Unexpected Codex release archive contents:\n%s\n' "$archive_entries" >&2
    exit 1
fi
tar -xOzf "$archive" "$CODEX_RELEASE_BINARY" >"$staging"
printf '%s  %s\n' "$CODEX_RELEASE_BINARY_SHA256" "$staging" | shasum -a 256 -c -

# OpenAI signs the uncompressed executable, while GitHub release metadata gives
# the independent archive and bundle hashes recorded above.
cosign verify-blob \
    --bundle "$sigstore" \
    --certificate-identity "$CODEX_RELEASE_CERT_IDENTITY" \
    --certificate-oidc-issuer "$CODEX_RELEASE_OIDC_ISSUER" \
    "$staging"

install -m 0755 "$staging" "$destination"
"$script_dir/verify-codex-artifact.sh" "$destination"
printf 'Packaged verified official Codex app-server %s for %s at %s\n' \
    "$CODEX_VERSION" "$CODEX_ANDROID_ABI" "$destination"
