#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

project_dir=${RAVA_PROJECT_DIR:-"$HOME/Rava"}
profile_dir=${RAVA_GEMINI_BROWSER_PROFILE:-"$HOME/.rava/gemini-chromium"}
display=${RAVA_DISPLAY:-":1"}
pid_file="$project_dir/.gemini-chromium.pid"

if curl -fsS http://127.0.0.1:9223/json >/dev/null 2>&1; then
  echo "Dedicated Gemini browser is already running on CDP port 9223."
  exit 0
fi

if [ -f "$pid_file" ]; then
  old_pid=$(cat "$pid_file")
  kill "$old_pid" 2>/dev/null || true
  sleep 1
fi

mkdir -p "$profile_dir"
export DISPLAY="$display"
dbus-launch chromium-browser \
  --no-sandbox \
  --disable-dev-shm-usage \
  --remote-debugging-address=127.0.0.1 \
  --remote-debugging-port=9223 \
  --user-data-dir="$profile_dir" \
  --window-position=0,0 \
  --window-size=896,1920 \
  https://gemini.google.com/app \
  >"$project_dir/.gemini-chromium.log" 2>&1 &
printf '%s\n' "$!" >"$pid_file"

echo "Dedicated Gemini login browser started on CDP port 9223."
