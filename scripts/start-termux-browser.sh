#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

project_dir=${RAVA_PROJECT_DIR:-"$HOME/Rava"}
profile_dir=${RAVA_BROWSER_PROFILE:-"$HOME/.rava/chromium"}
display=${RAVA_DISPLAY:-":1"}
start_url=${1:-"https://gemini.google.com/app"}

mkdir -p "$profile_dir"
for pid_file in "$project_dir/.chromium.pid" "$project_dir/.termux-x11.pid"; do
  if [ -f "$pid_file" ]; then
    old_pid=$(cat "$pid_file")
    kill "$old_pid" 2>/dev/null || true
  fi
done
sleep 1

termux-x11 "$display" >"$project_dir/.termux-x11.log" 2>&1 &
printf '%s\n' "$!" >"$project_dir/.termux-x11.pid"
sleep 1

export DISPLAY="$display"
dbus-launch chromium-browser \
  --no-sandbox \
  --disable-dev-shm-usage \
  --remote-debugging-address=127.0.0.1 \
  --remote-debugging-port=9222 \
  --user-data-dir="$profile_dir" \
  --window-position=0,0 \
  --window-size=896,1920 \
  "$start_url" >"$project_dir/.chromium.log" 2>&1 &
printf '%s\n' "$!" >"$project_dir/.chromium.pid"
