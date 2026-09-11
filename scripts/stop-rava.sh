#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

project_dir=${RAVA_PROJECT_DIR:-"$HOME/Rava"}
pid_file="$project_dir/.rava-engine.pid"

if [ ! -f "$pid_file" ]; then
  echo "Rava Engine is not running."
  exit 0
fi

pid=$(cat "$pid_file")
kill "$pid" 2>/dev/null || true
rm -f "$pid_file"
echo "Rava Engine stopped."
