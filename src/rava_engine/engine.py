from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator, Sequence

from .conversations import ConversationStore
from .errors import InvalidRequest
from .registry import ProviderRegistry
from .types import Conversation, Message, ModelInfo, ProviderStatus


class Engine:
    def __init__(self, registry: ProviderRegistry, conversations: ConversationStore | None = None):
        self.registry = registry
        self.conversations = conversations or ConversationStore()
        self._requests: dict[str, asyncio.Task[object]] = {}

    async def list_models(self) -> list[ModelInfo]:
        return await self.registry.list_models()

    async def statuses(self) -> list[ProviderStatus]:
        return await self.registry.statuses()

    async def create_conversation(self, app_id: str, model_id: str) -> Conversation:
        if not app_id.strip():
            raise InvalidRequest("app_id is required")
        provider, model = await self.registry.resolve_model(model_id)
        session = await provider.create_session(model)
        return self.conversations.create(app_id, model, session)

    async def complete(
        self,
        *,
        app_id: str,
        model_id: str,
        messages: Sequence[Message],
        conversation_id: str | None = None,
    ) -> tuple[Conversation, AsyncIterator[str]]:
        if not messages or not any(message.role == "user" for message in messages):
            raise InvalidRequest("At least one user message is required")

        if conversation_id:
            conversation = self.conversations.get(conversation_id, app_id)
            if conversation.model.id != model_id:
                raise InvalidRequest("A conversation model cannot be changed")
            provider = self.registry.provider(conversation.model.provider)
        else:
            conversation = await self.create_conversation(app_id, model_id)
            provider = self.registry.provider(conversation.model.provider)

        async def stream() -> AsyncIterator[str]:
            async with self.conversations.lock(conversation.id):
                async for chunk in provider.send(conversation.provider_session, messages):
                    yield chunk
                conversation.messages.extend(messages)

        return conversation, stream()

    def register_request(self, request_id: str, task: asyncio.Task[object]) -> None:
        self._requests[request_id] = task

    def unregister_request(self, request_id: str) -> None:
        self._requests.pop(request_id, None)

    def cancel_request(self, request_id: str) -> bool:
        task = self._requests.get(request_id)
        if task is None or task.done():
            return False
        task.cancel()
        return True

    async def close(self) -> None:
        await self.registry.close()
