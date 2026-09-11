#!/data/data/com.termux/files/usr/bin/bash
set -u

project_dir=${RAVA_PROJECT_DIR:-"$HOME/Rava"}
output_file="$project_dir/.gemini-smoke.out"
status_file="$project_dir/.gemini-smoke.status"

rm -f "$status_file"
"$project_dir/.venv/bin/python" "$project_dir/scripts/device-gemini-smoke.py" \
  >"$output_file" 2>&1
result=$?
printf '%s\n' "$result" >"$status_file"
exit "$result"
