#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

project_dir=${RAVA_PROJECT_DIR:-"$HOME/Rava"}
log_file="$project_dir/.full-install.log"
status_file="$project_dir/.full-install.status"

exec > >(tee "$log_file") 2>&1
rm -f "$status_file"

finish() {
  result=$?
  printf '%s\n' "$result" >"$status_file"
  echo "Rava full install finished with status $result"
}
trap finish EXIT

pkg update -y
pkg install -y python git rust clang make cmake pkg-config openssl libffi x11-repo
pkg install -y termux-x11-nightly dbus chromium
bash "$project_dir/scripts/device-bootstrap.sh"
bash "$project_dir/scripts/install-chatgpt-sidecar.sh"

echo "Rava and both web connectors are installed."
