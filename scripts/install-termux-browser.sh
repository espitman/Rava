#!/data/data/com.termux/files/usr/bin/bash
set -u

project_dir=${RAVA_PROJECT_DIR:-"$HOME/Rava"}
log_file="$project_dir/.browser-install.log"
status_file="$project_dir/.browser-install.status"

exec >"$log_file" 2>&1
rm -f "$status_file"

finish() {
  result=$?
  printf '%s\n' "$result" >"$status_file"
  echo "Rava browser install finished with status $result"
}
trap finish EXIT

set -e
pkg install -y termux-x11-nightly dbus chromium
