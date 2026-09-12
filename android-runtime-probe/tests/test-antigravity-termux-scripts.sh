#!/usr/bin/env bash
set -euo pipefail

test_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
probe_dir=$(cd -- "$test_dir/.." && pwd)
install_script="$probe_dir/scripts/install-antigravity-termux.sh"
runner_script="$probe_dir/scripts/run-antigravity-termux.sh"

# shellcheck source=../antigravity-termux.env
source "$probe_dir/antigravity-termux.env"

bash -n "$install_script" "$runner_script"
[[ "$AGY_VERSION" == 1.2.2 ]]
[[ "$AGY_ARCHIVE_SHA512" =~ ^[0-9a-f]{128}$ ]]
[[ "$AGY_BINARY_SHA256" =~ ^[0-9a-f]{64}$ ]]
[[ "$AGY_ARCHIVE_BYTES" == 54016481 ]]
[[ "$AGY_BINARY_BYTES" == 202985704 ]]

grep -Fq '"$agy_path" "$@"' "$runner_script"
grep -Fq 'hosts:/etc/hosts' "$runner_script"
grep -Fq 'resolv.conf:/etc/resolv.conf' "$runner_script"
grep -Fq 'GODEBUG=${GODEBUG:-netdns=go+1}' "$runner_script"
grep -Fq 'archive must contain only the antigravity executable' "$install_script"
if grep -Eq 'dangerously-skip-permissions|copy.*token|cat.*credential' \
  "$install_script" "$runner_script"; then
  printf '%s\n' 'Unsafe Antigravity option or credential access found.' >&2
  exit 1
fi

printf '%s\n' 'Antigravity Termux script checks passed.'
