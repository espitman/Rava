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

    async def send_message_stream(self, prompt: str):  # type: ignore[no-untyped-def]
        self.prompt = prompt
        yield FakeOutput(text="RAVA", text_delta="RAVA")
        yield FakeOutput(text="RAVA_OK", text_delta="_OK")


async def test_gemini_provider_yields_only_new_stream_characters() -> None:
    provider = GeminiWebProvider("cookie")
    session = FakeGeminiSession()

    chunks = [
        chunk
        async for chunk in provider.send(session, [Message("user", "Reply exactly")])
    ]

    assert chunks == ["RAVA", "_OK"]
    assert "Reply exactly" in (session.prompt or "")
