#!/data/data/com.termux/files/usr/bin/python
from __future__ import annotations

import json
import os
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path


def request_json(url: str) -> dict:
    request = urllib.request.Request(url, headers={"X-Rava-App-Id": "combined-smoke"})
    with urllib.request.urlopen(request, timeout=90) as response:
        return json.load(response)


def main() -> None:
    project_dir = Path(os.environ.get("RAVA_PROJECT_DIR", "~/Rava")).expanduser()
    port = int(os.environ.get("RAVA_COMBINED_SMOKE_PORT", "18769"))
    environment = os.environ.copy()
    environment["RAVA_PORT"] = str(port)
    log_path = project_dir / ".device-combined-smoke.log"

    with log_path.open("w") as log:
        process = subprocess.Popen(
            [str(project_dir / ".venv/bin/rava-engine")],
            cwd=project_dir,
            env=environment,
            stdout=log,
            stderr=subprocess.STDOUT,
        )
    try:
        base_url = f"http://127.0.0.1:{port}"
        for _ in range(100):
            try:
                health = request_json(f"{base_url}/health")
                break
            except (OSError, urllib.error.URLError):
                time.sleep(0.2)
        else:
            raise SystemExit("Rava did not become ready")

        providers = {item["id"]: item for item in health["providers"]}
        expected = {"chatgpt", "gemini"}
        if set(providers) != expected or not all(item["available"] for item in providers.values()):
            raise SystemExit(f"Providers are not jointly healthy: {health}")

        models = [item["id"] for item in request_json(f"{base_url}/v1/models")["data"]]
        counts = {
            provider: sum(model.startswith(f"{provider}/") for model in models)
            for provider in sorted(expected)
        }
        if not all(counts.values()):
            raise SystemExit(f"Missing provider models: {counts=}")
        print(json.dumps({"health": health, "model_counts": counts}))
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()


if __name__ == "__main__":
    main()
