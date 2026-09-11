#!/data/data/com.termux/files/usr/bin/python
from __future__ import annotations

import asyncio
import json
import os
import time
import urllib.request

import aiohttp


async def main() -> None:
    port = int(os.environ.get("RAVA_GEMINI_CDP_PORT", "9223"))
    with urllib.request.urlopen(f"http://127.0.0.1:{port}/json", timeout=3) as response:
        targets = json.load(response)
    page = next(target for target in targets if target.get("type") == "page")
    async with aiohttp.ClientSession() as session:
        async with session.ws_connect(page["webSocketDebuggerUrl"]) as websocket:
            await websocket.send_json({"id": 1, "method": "Network.getAllCookies"})
            async for message in websocket:
                payload = message.json()
                if payload.get("id") != 1:
                    continue
                for cookie in payload.get("result", {}).get("cookies", []):
                    if cookie.get("name") not in {"__Secure-1PSID", "__Secure-1PSIDTS"}:
                        continue
                    expires = cookie.get("expires", -1)
                    remaining = None if expires < 0 else int(expires - time.time())
                    print(
                        json.dumps(
                            {
                                "name": cookie["name"],
                                "domain": cookie.get("domain"),
                                "path": cookie.get("path"),
                                "value_length": len(cookie.get("value", "")),
                                "expires_in_seconds": remaining,
                                "secure": cookie.get("secure"),
                                "http_only": cookie.get("httpOnly"),
                                "same_site": cookie.get("sameSite"),
                            }
                        )
                    )
                return


if __name__ == "__main__":
    asyncio.run(main())
