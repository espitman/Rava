from __future__ import annotations

from collections.abc import AsyncIterator, Sequence
from typing import Any

from ..errors import ModelSelectionFailed, ProviderUnavailable
from ..provider import Provider
from ..types import Message, ModelInfo, ProviderStatus


class GeminiWebProvider(Provider):
    name = "gemini"

    def __init__(
        self,
        secure_1psid: str,
        secure_1psidts: str | None = None,
        *,
        temporary: bool = True,
    ) -> None:
        self._secure_1psid = secure_1psid
        self._secure_1psidts = secure_1psidts
        self._temporary = temporary
        self._client: Any = None

    async def _ensure_client(self) -> Any:
        if self._client is not None:
            return self._client
        if not self._secure_1psid:
            raise ProviderUnavailable("GEMINI_SECURE_1PSID is not configured")
        try:
            from gemini_webapi import GeminiClient
        except ImportError as exc:
            raise ProviderUnavailable("gemini-webapi is not installed") from exc
        client = GeminiClient(self._secure_1psid, self._secure_1psidts)
        await client.init(timeout=30, auto_close=False, auto_refresh=True)
        self._client = client
        return client

    async def list_models(self) -> list[ModelInfo]:
        client = await self._ensure_client()
        discovered = client.list_models() or []
        return [
            ModelInfo(
                provider=self.name,
                upstream_id=model.model_name,
                name=model.display_name,
                metadata={"model_id": model.model_id},
            )
            for model in discovered
        ]

    async def create_session(self, model: ModelInfo) -> Any:
        client = await self._ensure_client()
        try:
            resolved = client.resolve_model(model.upstream_id)
        except ValueError as exc:
            raise ModelSelectionFailed(
                f"Gemini model disappeared before selection: {model.id}"
            ) from exc
        return client.start_chat(model=resolved)

    async def send(self, session: Any, messages: Sequence[Message]) -> AsyncIterator[str]:
        prompt = _messages_to_prompt(messages)
        async for output in session.send_message_stream(prompt, temporary=self._temporary):
            # Gemini-API exposes the complete response in ``text`` and only the
            # newly arrived characters in ``text_delta``.  The engine's stream
            # contract requires deltas; yielding ``text`` repeats prior chunks.
            if output.text_delta:
                yield output.text_delta

    async def status(self) -> ProviderStatus:
        try:
            await self._ensure_client()
            return ProviderStatus(self.name, True)
        except Exception as exc:
            return ProviderStatus(self.name, False, str(exc))

    async def close(self) -> None:
        if self._client is not None:
            await self._client.close()
            self._client = None


def _messages_to_prompt(messages: Sequence[Message]) -> str:
    labels = {"system": "System", "user": "User", "assistant": "Assistant"}
    return "\n\n".join(f"[{labels.get(item.role, item.role)}]\n{item.content}" for item in messages)
