#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

project_dir=${RAVA_PROJECT_DIR:-"$HOME/Rava"}
pid_file="$project_dir/.rava-engine.pid"
log_file="$project_dir/.rava-engine.log"

if [ -f "$pid_file" ]; then
  old_pid=$(cat "$pid_file")
  if kill -0 "$old_pid" 2>/dev/null; then
    echo "Rava Engine is already running (PID $old_pid)."
    exit 0
  fi
  rm -f "$pid_file"
fi

cd "$project_dir"
nohup "$project_dir/.venv/bin/rava-engine" >"$log_file" 2>&1 &
pid=$!
printf '%s\n' "$pid" >"$pid_file"
sleep 1
if ! kill -0 "$pid" 2>/dev/null; then
  cat "$log_file"
  rm -f "$pid_file"
  exit 1
fi
echo "Rava Engine started on http://127.0.0.1:8766 (PID $pid)."
