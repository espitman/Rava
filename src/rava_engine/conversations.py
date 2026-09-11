from __future__ import annotations

import asyncio
import uuid

from .errors import ConversationAccessDenied, ConversationNotFound
from .types import Conversation, ModelInfo


class ConversationStore:
    def __init__(self) -> None:
        self._items: dict[str, Conversation] = {}
        self._locks: dict[str, asyncio.Lock] = {}

    def create(self, app_id: str, model: ModelInfo, provider_session: object) -> Conversation:
        conversation_id = f"conv_{uuid.uuid4().hex}"
        conversation = Conversation(
            id=conversation_id,
            app_id=app_id,
            model=model,
            provider_session=provider_session,
        )
        self._items[conversation_id] = conversation
        self._locks[conversation_id] = asyncio.Lock()
        return conversation

    def get(self, conversation_id: str, app_id: str) -> Conversation:
        conversation = self._items.get(conversation_id)
        if conversation is None:
            raise ConversationNotFound(f"Unknown conversation: {conversation_id}")
        if conversation.app_id != app_id:
            raise ConversationAccessDenied("Conversation belongs to another app")
        return conversation

    def lock(self, conversation_id: str) -> asyncio.Lock:
        try:
            return self._locks[conversation_id]
        except KeyError as exc:
            raise ConversationNotFound(f"Unknown conversation: {conversation_id}") from exc

    def delete(self, conversation_id: str, app_id: str) -> None:
        self.get(conversation_id, app_id)
        del self._items[conversation_id]
        del self._locks[conversation_id]
