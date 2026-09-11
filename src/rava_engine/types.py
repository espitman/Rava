from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True, slots=True)
class ModelInfo:
    provider: str
    upstream_id: str
    name: str
    metadata: dict[str, Any] = field(default_factory=dict)

    @property
    def id(self) -> str:
        return f"{self.provider}/{self.upstream_id}"


@dataclass(frozen=True, slots=True)
class Message:
    role: str
    content: str


@dataclass(slots=True)
class Conversation:
    id: str
    app_id: str
    model: ModelInfo
    provider_session: Any
    messages: list[Message] = field(default_factory=list)


@dataclass(frozen=True, slots=True)
class ProviderStatus:
    provider: str
    available: bool
    detail: str | None = None
