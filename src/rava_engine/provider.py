from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import AsyncIterator, Sequence
from typing import Any

from .types import Message, ModelInfo, ProviderStatus


class Provider(ABC):
    """Transport-independent provider contract used by HTTP and future AIDL layers."""

    name: str

    @abstractmethod
    async def list_models(self) -> list[ModelInfo]:
        """Return models actually available to the logged-in account."""

    @abstractmethod
    async def create_session(self, model: ModelInfo) -> Any:
        """Create provider-specific conversation state for an already resolved model."""

    @abstractmethod
    async def send(
        self,
        session: Any,
        messages: Sequence[Message],
    ) -> AsyncIterator[str]:
        """Yield response deltas."""
        if False:
            yield ""

    @abstractmethod
    async def status(self) -> ProviderStatus:
        """Return current provider availability without raising."""

    async def delete_session(self, session: Any) -> None:
        """Delete the provider-side conversation represented by a session."""

    async def close(self) -> None:
        """Release provider resources."""
