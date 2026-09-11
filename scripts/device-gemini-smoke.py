#!/data/data/com.termux/files/usr/bin/python
from __future__ import annotations

import json
import os
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path


def request_json(url: str, body: dict | None = None) -> dict:
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json", "X-Rava-App-Id": "device-smoke"},
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        return json.load(response)


def request_error_json(url: str, body: dict) -> tuple[int, dict]:
    try:
        request_json(url, body)
    except urllib.error.HTTPError as exc:
        return exc.code, json.load(exc)
    raise SystemExit("Expected the request to fail")


def request_sse(url: str, body: dict) -> tuple[str, str]:
    data = json.dumps(body).encode()
    request = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json", "X-Rava-App-Id": "device-smoke"},
    )
    chunks: list[str] = []
    conversation_id = ""
    with urllib.request.urlopen(request, timeout=90) as response:
        if not response.headers.get_content_type() == "text/event-stream":
            raise SystemExit("Streaming response is not SSE")
        for raw_line in response:
            line = raw_line.decode().strip()
            if not line.startswith("data: "):
                continue
            data_line = line.removeprefix("data: ")
            if data_line == "[DONE]":
                break
            payload = json.loads(data_line)
            conversation_id = payload["conversation_id"]
            chunks.append(payload["choices"][0]["delta"].get("content", ""))
    return conversation_id, "".join(chunks).strip()


def main() -> None:
    project_dir = Path(os.environ.get("RAVA_PROJECT_DIR", "~/Rava")).expanduser()
    port = int(os.environ.get("RAVA_GEMINI_SMOKE_PORT", "18767"))
    environment = os.environ.copy()
    environment["CHATGPT_WEB2API_URL"] = ""
    environment["RAVA_PORT"] = str(port)
    log_path = project_dir / ".device-gemini-smoke.log"

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
        for _ in range(50):
            try:
                health = request_json(f"{base_url}/health")
                break
            except (OSError, urllib.error.URLError):
                time.sleep(0.2)
        else:
            raise SystemExit("Rava did not become ready")

        models_response = request_json(f"{base_url}/v1/models")
        models = [item["id"] for item in models_response["data"]]
        if not models:
            raise SystemExit(f"No Gemini models discovered; health={health}")

        model = models[0]
        status, invalid = request_error_json(
            f"{base_url}/v1/chat/completions",
            {
                "model": "gemini/definitely-not-a-real-model",
                "messages": [{"role": "user", "content": "hello"}],
            },
        )
        if status != 404 or invalid.get("error", {}).get("code") != "model_not_found":
            raise SystemExit(f"Invalid model did not fail closed: {status=} {invalid=}")

        completion = request_json(
            f"{base_url}/v1/chat/completions",
            {
                "model": model,
                "messages": [
                    {
                        "role": "user",
                        "content": "Reply with exactly RAVA_OK and nothing else.",
                    }
                ],
                "stream": False,
            },
        )
        answer = completion["choices"][0]["message"]["content"].strip()
        if answer != "RAVA_OK":
            raise SystemExit(f"Unexpected ordinary response: {answer!r}")

        conversation_id = completion["conversation_id"]
        continuation = request_json(
            f"{base_url}/v1/chat/completions",
            {
                "model": model,
                "conversation_id": conversation_id,
                "messages": [
                    {
                        "role": "user",
                        "content": "Reply with exactly RAVA_CONTINUE_OK and nothing else.",
                    }
                ],
                "stream": False,
            },
        )
        continued_answer = continuation["choices"][0]["message"]["content"].strip()
        if continued_answer != "RAVA_CONTINUE_OK":
            raise SystemExit(f"Unexpected continued response: {continued_answer!r}")

        stream_conversation_id, streamed_answer = request_sse(
            f"{base_url}/v1/chat/completions",
            {
                "model": model,
                "messages": [
                    {
                        "role": "user",
                        "content": "Reply with exactly RAVA_STREAM_OK and nothing else.",
                    }
                ],
                "stream": True,
            },
        )
        if streamed_answer != "RAVA_STREAM_OK":
            raise SystemExit(f"Unexpected streamed response: {streamed_answer!r}")

        print(
            json.dumps(
                {
                    "health": health,
                    "models": models,
                    "tested": model,
                    "ordinary": answer,
                    "continued": continued_answer,
                    "streamed": streamed_answer,
                    "conversation_id": conversation_id,
                    "stream_conversation_id": stream_conversation_id,
                    "invalid_model": invalid["error"]["code"],
                }
            )
        )
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()


if __name__ == "__main__":
    main()
