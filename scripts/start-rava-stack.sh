#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

project_dir=${RAVA_PROJECT_DIR:-"$HOME/Rava"}

command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true
bash "$project_dir/scripts/start-termux-browser.sh" https://chatgpt.com/
sleep 5
bash "$project_dir/scripts/start-chatgpt-sidecar.sh"
bash "$project_dir/scripts/start-gemini-login-browser.sh"
sleep 5
"$project_dir/.venv/bin/python" "$project_dir/scripts/capture-gemini-session.py"

sidecar_ready=0
for _ in {1..60}; do
  if curl -fsS --max-time 5 http://127.0.0.1:8080/v1/models 2>/dev/null | grep -q '"data"'; then
    sidecar_ready=1
    break
  fi
  sleep 1
done
if [ "$sidecar_ready" -ne 1 ]; then
  echo "ChatGPT sidecar did not become healthy; see $project_dir/.chatgpt-sidecar.log" >&2
  exit 1
fi

bash "$project_dir/scripts/stop-rava.sh"
bash "$project_dir/scripts/start-rava.sh"

echo "Rava stack is ready. Keep Termux:X11 and the two Chromium profiles running."
