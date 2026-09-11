#!/data/data/com.termux/files/usr/bin/python
from __future__ import annotations

import asyncio
import json
import os
import urllib.request
from pathlib import Path

import aiohttp


def cdp_port() -> int:
    return int(os.environ.get("RAVA_GEMINI_CDP_PORT", "9223"))


async def get_google_cookies() -> dict[str, str]:
    with urllib.request.urlopen(f"http://127.0.0.1:{cdp_port()}/json", timeout=3) as response:
        targets = json.load(response)
    page = next(
        (target for target in targets if target.get("type") == "page"),
        None,
    )
    if page is None:
        raise SystemExit("No Chromium page is available through CDP")

    async with aiohttp.ClientSession() as session:
        async with session.ws_connect(page["webSocketDebuggerUrl"]) as websocket:
            await websocket.send_json({"id": 1, "method": "Network.getAllCookies"})
            async for message in websocket:
                payload = message.json()
                if payload.get("id") != 1:
                    continue
                cookies = payload.get("result", {}).get("cookies", [])
                return {
                    item["name"]: item["value"]
                    for item in cookies
                    if item.get("domain", "").endswith("google.com")
                    and item.get("name") in {"__Secure-1PSID", "__Secure-1PSIDTS"}
                }
    raise SystemExit("Chromium closed the CDP connection")


async def close_capture_browser() -> None:
    if os.environ.get("RAVA_GEMINI_CLOSE_BROWSER", "0") != "1":
        return
    with urllib.request.urlopen(f"http://127.0.0.1:{cdp_port()}/json", timeout=3) as response:
        targets = json.load(response)
    page = next((target for target in targets if target.get("type") == "page"), None)
    if page is None:
        return
    async with aiohttp.ClientSession() as session:
        async with session.ws_connect(page["webSocketDebuggerUrl"]) as websocket:
            await websocket.send_json({"id": 2, "method": "Browser.close"})


async def main() -> None:
    cookies = await get_google_cookies()
    secure_1psid = cookies.get("__Secure-1PSID")
    if not secure_1psid:
        raise SystemExit("Gemini login cookie was not found; open Gemini after signing in")

    config_path = Path(
        os.environ.get("RAVA_CONFIG_FILE", "~/.config/rava/config.json")
    ).expanduser()
    try:
        config = json.loads(config_path.read_text())
    except FileNotFoundError:
        config = {}
    if not isinstance(config, dict):
        raise SystemExit(f"Config must contain a JSON object: {config_path}")

    config["gemini_secure_1psid"] = secure_1psid
    if secure_1psidts := cookies.get("__Secure-1PSIDTS"):
        config["gemini_secure_1psidts"] = secure_1psidts
    else:
        config.pop("gemini_secure_1psidts", None)

    config_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = config_path.with_suffix(".tmp")
    temporary.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n")
    temporary.chmod(0o600)
    temporary.replace(config_path)
    config_path.chmod(0o600)
    await close_capture_browser()
    print(
        "Gemini session captured securely "
        f"(1PSID=yes, 1PSIDTS={'yes' if '__Secure-1PSIDTS' in cookies else 'no'}); "
        + (
            "dedicated browser closed"
            if os.environ.get("RAVA_GEMINI_CLOSE_BROWSER", "0") == "1"
            else "keep the dedicated browser running"
        )
    )


if __name__ == "__main__":
    asyncio.run(main())
