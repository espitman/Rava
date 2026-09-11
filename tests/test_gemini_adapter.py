from __future__ import annotations

from dataclasses import dataclass

from rava_engine.adapters.gemini import GeminiWebProvider
from rava_engine.types import Message


@dataclass
class FakeOutput:
    text: str
    text_delta: str


class FakeGeminiSession:
    def __init__(self) -> None:
        self.prompt: str | None = None
        self.temporary: bool | None = None

    async def send_message(self, prompt: str, *, temporary: bool = False):  # type: ignore[no-untyped-def]
        self.prompt = prompt
        self.temporary = temporary
        return FakeOutput(text="RAVA_OK", text_delta="_OK")


async def test_gemini_provider_yields_completed_response() -> None:
    provider = GeminiWebProvider("cookie")
    session = FakeGeminiSession()

    chunks = [
        chunk
        async for chunk in provider.send(session, [Message("user", "Reply exactly")])
    ]

    assert chunks == ["RAVA_OK"]
    assert "Reply exactly" in (session.prompt or "")
    assert session.temporary is True
