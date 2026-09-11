#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

project_dir=${RAVA_PROJECT_DIR:-"$HOME/Rava"}
port=${RAVA_SMOKE_PORT:-18766}
log_file="$project_dir/.device-engine-smoke.log"

cd "$project_dir"
CHATGPT_WEB2API_URL= RAVA_PORT="$port" .venv/bin/rava-engine >"$log_file" 2>&1 &
engine_pid=$!
trap 'kill "$engine_pid" 2>/dev/null || true' EXIT

.venv/bin/python - "$port" <<'PY'
import json
import sys
import time
import urllib.request

port = int(sys.argv[1])
base_url = f"http://127.0.0.1:{port}"

for _ in range(40):
    try:
        with urllib.request.urlopen(f"{base_url}/health", timeout=1) as response:
            health = json.load(response)
        break
    except OSError:
        time.sleep(0.1)
else:
    raise SystemExit("Rava did not become ready")

with urllib.request.urlopen(f"{base_url}/v1/models", timeout=2) as response:
    models = json.load(response)

assert health == {"status": "degraded", "providers": []}, health
assert models == {"object": "list", "data": []}, models
print(json.dumps({"health": health, "models": models}, ensure_ascii=False))
PY
