#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

project_dir=${RAVA_PROJECT_DIR:-"$HOME/Rava"}
venv_dir="$project_dir/.rava/chatgpt-web2api-venv"
profile_dir=${RAVA_BROWSER_PROFILE:-"$HOME/.rava/chromium"}
pid_file="$project_dir/.chatgpt-sidecar.pid"
log_file="$project_dir/.chatgpt-sidecar.log"

if ! curl --silent --fail --max-time 2 http://127.0.0.1:9222/json/version >/dev/null; then
  echo "Chromium CDP is not running. Start the Termux browser first." >&2
  exit 1
fi

if [ -f "$pid_file" ]; then
  old_pid=$(cat "$pid_file")
  kill "$old_pid" 2>/dev/null || true
  sleep 1
fi

nohup "$venv_dir/bin/chatgpt-web2api" start \
  --host 127.0.0.1 \
  --port 8080 \
  --cdp-port 9222 \
  --chrome-path "$PREFIX/bin/chromium-browser" \
  --user-data-dir "$profile_dir" \
  --log-level INFO \
  >"$log_file" 2>&1 &
printf '%s\n' "$!" >"$pid_file"

echo "ChatGPT sidecar started; log: $log_file"
