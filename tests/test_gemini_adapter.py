from __future__ import annotations

from dataclasses import dataclass

from rava_engine.adapters.gemini import GeminiWebProvider
from rava_engine.types import Message


@dataclass
class FakeOutput:
    text: str
    text_delta: str
    images: list[object] | None = None

    def __post_init__(self) -> None:
        if self.images is None:
            self.images = []


@dataclass
class FakeImage:
    alt: str
    title: str
    url: str

    async def save(self, path: str, filename: str) -> str:
        return f"{path}/{filename}.jpg"


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


async def test_gemini_provider_preserves_images_as_markdown() -> None:
    provider = GeminiWebProvider("cookie")
    session = FakeGeminiSession()

    async def send_message(prompt: str, *, temporary: bool = False):  # type: ignore[no-untyped-def]
        session.prompt = prompt
        session.temporary = temporary
        return FakeOutput(
            text="_0\n\nA nebula",
            text_delta="",
            images=[
                FakeImage(
                    alt="Space", title="Image", url="https://img.test/space.jpg"
                )
            ],
        )

    session.send_message = send_message  # type: ignore[method-assign]
    chunks = [
        chunk
        async for chunk in provider.send(session, [Message("user", "show space")])
    ]

    assert len(chunks) == 1
    assert chunks[0].startswith("A nebula\n\n![Space](/v1/media/")
    assert chunks[0].endswith(".jpg)")
