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

[[ "$(stat -c %s "$agy_path")" == "$AGY_BINARY_BYTES" ]]
[[ "$(sha256sum "$agy_path" | cut -d ' ' -f1)" == "$AGY_BINARY_SHA256" ]]
[[ -r "$runtime_dir/hosts" && -r "$runtime_dir/resolv.conf" ]]
[[ "$(sha256sum "$runtime_dir/hosts" | cut -d ' ' -f1)" == \
  00e987d761e447af9e2d73ff09ccc896273948c756a71d5bfb8db3a73dc2eb54 ]]
[[ "$(sha256sum "$runtime_dir/resolv.conf" | cut -d ' ' -f1)" == \
  7e8ad76e0d200e93918ca2e93c99ff8ecd02071953bf1479819db3ac0dbb6d07 ]]
[[ "$(sha256sum "$runtime_dir/chat-settings.json" | cut -d ' ' -f1)" == \
  33a7cf8672dbeacd8107baf06b396db85483d096b3f1ef9f947986090130b4fd ]]
[[ -x "$prefix/bin/proot" && -x "$prefix/glibc/bin/ld.so" ]]
[[ -r "$prefix/glibc/etc/ssl/certs/ca-certificates.crt" ]]

export GODEBUG=${GODEBUG:-netdns=go+1}
export SSL_CERT_FILE=${SSL_CERT_FILE:-"$prefix/glibc/etc/ssl/certs/ca-certificates.crt"}

# The official CLI uses a documented URL-and-code flow when it detects Remote
# SSH. Set this only for a visible, interactive sign-in run.
if [[ "${RAVA_AGY_REMOTE_AUTH:-0}" == 1 ]]; then
  export SSH_CONNECTION=${SSH_CONNECTION:-'127.0.0.1 49152 127.0.0.1 22'}
  export SSH_TTY=${SSH_TTY:-/dev/pts/0}
fi

proot_args=(
  -b "$runtime_dir/hosts:/etc/hosts"
  -b "$runtime_dir/resolv.conf:/etc/resolv.conf"
)
chat_settings=
if [[ "${RAVA_AGY_CHAT_ONLY:-0}" == 1 ]]; then
  # Overlay a deny-all tool policy only for Rava's process. This launcher does
  # not inspect credentials; the official CLI accesses its own saved login.
  [[ -d "$home_dir/.gemini/antigravity-cli" ]]
  chat_settings=$(mktemp "$runtime_dir/chat-settings.XXXXXX")
  cp "$runtime_dir/chat-settings.json" "$chat_settings"
  chmod 600 "$chat_settings"
  trap 'find "$chat_settings" -maxdepth 0 -delete 2>/dev/null || true' EXIT
  proot_args+=(
    -b "$chat_settings:$home_dir/.gemini/antigravity-cli/settings.json"
    -w "$runtime_dir/workspace"
  )
fi

# Invoke the packaged glibc loader directly so every CLI argument remains one
# argument. Termux glibc-runner 2.0-3 expands $@ unquoted; that breaks prompts or
# file paths containing spaces. The Google executable remains byte-for-byte intact.
command=(
  "$prefix/bin/proot"
  "${proot_args[@]}"
  "$prefix/glibc/bin/ld.so"
  --library-path "$prefix/glibc/lib"
  "$agy_path" "$@"
)
if [[ -n "$chat_settings" ]]; then
  "${command[@]}"
else
  exec "${command[@]}"
fi
