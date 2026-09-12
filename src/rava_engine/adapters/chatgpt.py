from __future__ import annotations

import json
from collections.abc import AsyncIterator, Sequence
from typing import Any

import aiohttp

from ..errors import ProviderUnavailable
from ..provider import Provider
from ..types import Message, ModelInfo, ProviderStatus


class ChatGPTWeb2APIProvider(Provider):
    """Adapter for an Octo-Lex/ChatGPT-Web2API sidecar."""

    name = "chatgpt"

    def __init__(
        self,
        base_url: str,
        api_key: str | None = None,
        *,
        project_name: str | None = "Rava",
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._project_name = project_name
        self._project_id: str | None = None
        self._http: aiohttp.ClientSession | None = None

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self._api_key}"} if self._api_key else {}

    async def _session(self) -> aiohttp.ClientSession:
        if self._http is None or self._http.closed:
            self._http = aiohttp.ClientSession(headers=self._headers())
        return self._http

    async def list_models(self) -> list[ModelInfo]:
        http = await self._session()
        try:
            async with http.get(f"{self._base_url}/v1/models") as response:
                payload = await response.json()
                if response.status != 200:
                    raise ProviderUnavailable(_error_message(payload, response.status))
        except aiohttp.ClientError as exc:
            raise ProviderUnavailable(f"ChatGPT-Web2API is unreachable: {exc}") from exc
        return [
            ModelInfo(self.name, item["id"], item["id"], {"owned_by": item.get("owned_by")})
            for item in payload.get("data", [])
            if item.get("id")
        ]

    async def create_session(self, model: ModelInfo) -> dict[str, Any]:
        # The strict model-selection patch is tracked in PLAN.md. Until the sidecar
        # exposes selected-model proof, the upstream model id is retained per session.
        return {"model": model.upstream_id, "conversation_id": None}

    async def send(
        self, session: dict[str, Any], messages: Sequence[Message]
    ) -> AsyncIterator[str]:
        http = await self._session()
        body: dict[str, Any] = {
            "model": session["model"],
            "messages": [{"role": item.role, "content": item.content} for item in messages],
            "stream": True,
        }
        project_id = await self._ensure_project()
        if project_id:
            body["project_id"] = project_id
        if session["conversation_id"]:
            body["conversation_id"] = session["conversation_id"]
        try:
            async with http.post(f"{self._base_url}/v1/chat/completions", json=body) as response:
                if response.status != 200:
                    payload = await response.json(content_type=None)
                    raise ProviderUnavailable(_error_message(payload, response.status))
                async for payload in _sse_payloads(response.content):
                    if payload.get("conversation_id"):
                        session["conversation_id"] = payload["conversation_id"]
                    choices = payload.get("choices") or []
                    if not choices:
                        continue
                    choice = choices[0]
                    content = choice.get("delta", {}).get("content", "")
                    if content:
                        yield str(content)
                    if choice.get("finish_reason") == "error":
                        raise ProviderUnavailable(content or "ChatGPT-Web2API stream failed")
        except aiohttp.ClientError as exc:
            raise ProviderUnavailable(f"ChatGPT-Web2API request failed: {exc}") from exc

    async def _ensure_project(self) -> str | None:
        if not self._project_name:
            return None
        if self._project_id:
            return self._project_id
        http = await self._session()
        try:
            async with http.get(f"{self._base_url}/v1/projects") as response:
                payload = await response.json(content_type=None)
                if response.status != 200:
                    raise ProviderUnavailable(_error_message(payload, response.status))
            for project in payload.get("data", []):
                if str(project.get("name", "")).casefold() == self._project_name.casefold():
                    self._project_id = str(project["id"])
                    return self._project_id
            async with http.post(
                f"{self._base_url}/v1/projects",
                json={"name": self._project_name, "memory_scope": "project_v2"},
            ) as response:
                payload = await response.json(content_type=None)
                if response.status not in {200, 201} or not payload.get("id"):
                    raise ProviderUnavailable(_error_message(payload, response.status))
                self._project_id = str(payload["id"])
                return self._project_id
        except aiohttp.ClientError as exc:
            raise ProviderUnavailable(f"Could not prepare ChatGPT project: {exc}") from exc

    async def status(self) -> ProviderStatus:
        http = await self._session()
        try:
            async with http.get(f"{self._base_url}/health") as response:
                payload = await response.json()
                if response.status == 200:
                    return ProviderStatus(self.name, True, payload.get("status"))
                return ProviderStatus(self.name, False, _error_message(payload, response.status))
        except Exception as exc:
            return ProviderStatus(self.name, False, str(exc))

    async def delete_session(self, session: dict[str, Any]) -> None:
        conversation_id = session.get("conversation_id")
        if not conversation_id:
            return
        http = await self._session()
        try:
            async with http.delete(
                f"{self._base_url}/v1/conversations/{conversation_id}"
            ) as response:
                if response.status not in {200, 204}:
                    payload = await response.json(content_type=None)
                    raise ProviderUnavailable(_error_message(payload, response.status))
        except aiohttp.ClientError as exc:
            raise ProviderUnavailable(f"Could not delete the ChatGPT conversation: {exc}") from exc

    async def close(self) -> None:
        if self._http is not None:
            await self._http.close()
            self._http = None


def _error_message(payload: Any, status: int) -> str:
    if isinstance(payload, dict):
        error = payload.get("error")
        if isinstance(error, dict) and error.get("message"):
            return str(error["message"])
    return f"Upstream returned HTTP {status}: {json.dumps(payload, ensure_ascii=False)}"


async def _sse_payloads(lines: AsyncIterator[bytes]) -> AsyncIterator[dict[str, Any]]:
    async for raw_line in lines:
        line = raw_line.decode("utf-8").strip()
        if not line.startswith("data: "):
            continue
        data = line.removeprefix("data: ")
        if data == "[DONE]":
            return
        try:
            payload = json.loads(data)
        except json.JSONDecodeError as exc:
            raise ProviderUnavailable("ChatGPT-Web2API returned malformed SSE") from exc
        if isinstance(payload, dict):
            yield payload
