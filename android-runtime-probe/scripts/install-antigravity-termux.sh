#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
probe_dir=$(cd -- "$script_dir/.." && pwd)
# shellcheck source=../antigravity-termux.env
source "$probe_dir/antigravity-termux.env"

prefix=${PREFIX:-/data/data/com.termux/files/usr}
home_dir=${HOME:-/data/data/com.termux/files/home}
runtime_dir="$home_dir/.local/share/rava-antigravity/etc"
agy_path="$home_dir/.local/bin/agy"
mode=${1:-install}

fail() {
  printf 'Antigravity Termux setup: %s\n' "$*" >&2
  exit 1
}

package_version() {
  dpkg-query -W -f='${Version}' "$1" 2>/dev/null || true
}

verify_package() {
  local package_name=$1 expected=$2 actual
  actual=$(package_version "$package_name")
  [[ "$actual" == "$expected" ]] ||
    fail "$package_name version mismatch (expected $expected, found ${actual:-missing})"
}

verify_binary() {
  [[ -f "$agy_path" ]] || return 1
  [[ "$(stat -c %s "$agy_path")" == "$AGY_BINARY_BYTES" ]] || return 1
  [[ "$(sha256sum "$agy_path" | cut -d ' ' -f1)" == "$AGY_BINARY_SHA256" ]]
}

write_runtime_files() {
  install -d -m 700 "$runtime_dir" "$runtime_dir/workspace"
  printf '127.0.0.1 localhost\n::1 localhost ip6-localhost\n' >"$runtime_dir/hosts"
  printf 'nameserver 8.8.8.8\nnameserver 8.8.4.4\n' >"$runtime_dir/resolv.conf"
  cat >"$runtime_dir/chat-settings.json" <<'JSON'
{
  "toolPermission": "request-review",
  "allowNonWorkspaceAccess": false,
  "permissions": {
    "allow": [],
    "ask": [],
    "deny": [
      "read_file(*)",
      "write_file(*)",
      "read_url(*)",
      "execute_url(*)",
      "command(*)",
      "unsandboxed(*)",
      "mcp(*)"
    ]
  }
}
JSON
  chmod 600 "$runtime_dir/hosts" "$runtime_dir/resolv.conf"
  chmod 400 "$runtime_dir/chat-settings.json"
}

verify_installation() {
  [[ "$(uname -m)" == aarch64 ]] || fail 'this pin is only for Android arm64/aarch64'
  [[ "$prefix" == /data/data/com.termux/files/usr ]] || fail "unexpected PREFIX: $prefix"
  verify_package glibc-repo "$GLIBC_REPO_VERSION"
  verify_package glibc-runner "$GLIBC_RUNNER_VERSION"
  verify_package glibc "$GLIBC_VERSION"
  verify_package proot "$PROOT_VERSION"
  verify_package libtalloc "$LIBTALLOC_VERSION"
  verify_binary || fail 'agy binary is missing or does not match its pinned size and SHA-256'
  [[ -f "$runtime_dir/hosts" && -f "$runtime_dir/resolv.conf" ]] ||
    fail 'private resolver files are missing'
  [[ -f "$runtime_dir/chat-settings.json" ]] || fail 'chat-only settings are missing'
  [[ "$(sha256sum "$runtime_dir/hosts" | cut -d ' ' -f1)" == \
    00e987d761e447af9e2d73ff09ccc896273948c756a71d5bfb8db3a73dc2eb54 ]] ||
    fail 'private hosts file mismatch'
  [[ "$(sha256sum "$runtime_dir/resolv.conf" | cut -d ' ' -f1)" == \
    7e8ad76e0d200e93918ca2e93c99ff8ecd02071953bf1479819db3ac0dbb6d07 ]] ||
    fail 'private resolver file mismatch'
  [[ "$(sha256sum "$runtime_dir/chat-settings.json" | cut -d ' ' -f1)" == \
    33a7cf8672dbeacd8107baf06b396db85483d096b3f1ef9f947986090130b4fd ]] ||
    fail 'chat-only settings mismatch'
  printf 'Verified Antigravity CLI %s at %s\n' "$AGY_VERSION" "$agy_path"
}

case "$mode" in
  verify)
    verify_installation
    exit 0
    ;;
  install)
    ;;
  *)
    fail 'usage: install-antigravity-termux.sh [install|verify]'
    ;;
esac

[[ "$(uname -m)" == aarch64 ]] || fail 'this pin is only for Android arm64/aarch64'
[[ "$prefix" == /data/data/com.termux/files/usr ]] || fail "run this inside the official Termux app"

# These exact official Termux packages provide the GNU/Linux loader and a small
# private /etc view. No distro is installed and existing packages are not upgraded.
apt-get update
apt-get install -y --no-install-recommends "glibc-repo=$GLIBC_REPO_VERSION"
apt-get update
apt-get install -y --no-install-recommends \
  "glibc=$GLIBC_VERSION" \
  "glibc-runner=$GLIBC_RUNNER_VERSION" \
  "proot=$PROOT_VERSION" \
  "libtalloc=$LIBTALLOC_VERSION"

install -d -m 700 "$home_dir/.local/bin" "$home_dir/.gemini/antigravity-cli"
write_runtime_files
if verify_binary; then
  verify_installation
  printf '%s\n' 'Installation repaired and verified; the pinned CLI binary was reused.'
  exit 0
fi

install -d -m 700 "$home_dir/.cache"
download_dir=$(mktemp -d "$home_dir/.cache/rava-agy-install.XXXXXX")
trap 'find "$download_dir" -depth -delete 2>/dev/null || true' EXIT
archive="$download_dir/antigravity.tar.gz"
curl --fail --location --proto '=https' --tlsv1.2 --output "$archive" "$AGY_URL"
[[ "$(stat -c %s "$archive")" == "$AGY_ARCHIVE_BYTES" ]] || fail 'archive size mismatch'
[[ "$(sha512sum "$archive" | cut -d ' ' -f1)" == "$AGY_ARCHIVE_SHA512" ]] ||
  fail 'archive SHA-512 mismatch'

mapfile -t archive_entries < <(tar -tzf "$archive")
[[ "${#archive_entries[@]}" == 1 && "${archive_entries[0]}" == antigravity ]] ||
  fail 'archive must contain only the antigravity executable'
tar -xzf "$archive" -C "$download_dir"
[[ "$(stat -c %s "$download_dir/antigravity")" == "$AGY_BINARY_BYTES" ]] ||
  fail 'extracted executable size mismatch'
[[ "$(sha256sum "$download_dir/antigravity" | cut -d ' ' -f1)" == "$AGY_BINARY_SHA256" ]] ||
  fail 'extracted executable SHA-256 mismatch'

install -m 700 "$download_dir/antigravity" "$agy_path"

verify_installation
printf '%s\n' 'Installation complete. This script did not start sign-in or read credentials.'
