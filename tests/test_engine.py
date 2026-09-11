from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator, Sequence

import pytest

from rava_engine.engine import Engine
from rava_engine.errors import ConversationAccessDenied, InvalidRequest, ModelNotFound
from rava_engine.provider import Provider
from rava_engine.registry import ProviderRegistry
from rava_engine.types import Message, ModelInfo, ProviderStatus


class FakeProvider(Provider):
    name = "fake"

    async def list_models(self) -> list[ModelInfo]:
        return [ModelInfo(self.name, "exact", "Exact")]

    async def create_session(self, model: ModelInfo) -> dict[str, str]:
        return {"model": model.upstream_id}

    async def send(
        self, session: object, messages: Sequence[Message]
    ) -> AsyncIterator[str]:
        yield "answer:"
        yield messages[-1].content

    async def status(self) -> ProviderStatus:
        return ProviderStatus(self.name, True)


@pytest.fixture
def engine() -> Engine:
    return Engine(ProviderRegistry([FakeProvider()]))


async def test_models_are_namespaced(engine: Engine) -> None:
    models = await engine.list_models()
    assert [model.id for model in models] == ["fake/exact"]


async def test_unknown_model_fails_closed(engine: Engine) -> None:
    with pytest.raises(ModelNotFound):
        await engine.create_conversation("app.one", "fake/missing")


async def test_conversation_is_isolated_by_app(engine: Engine) -> None:
    conversation = await engine.create_conversation("app.one", "fake/exact")
    with pytest.raises(ConversationAccessDenied):
        await engine.complete(
            app_id="app.two",
            model_id="fake/exact",
            messages=[Message("user", "hello")],
            conversation_id=conversation.id,
        )


async def test_conversation_model_cannot_change(engine: Engine) -> None:
    conversation = await engine.create_conversation("app.one", "fake/exact")
    with pytest.raises(InvalidRequest):
        await engine.complete(
            app_id="app.one",
            model_id="fake/other",
            messages=[Message("user", "hello")],
            conversation_id=conversation.id,
        )


async def test_completion_streams_and_records_messages(engine: Engine) -> None:
    conversation, chunks = await engine.complete(
        app_id="app.one",
        model_id="fake/exact",
        messages=[Message("user", "hello")],
    )
    assert "".join([chunk async for chunk in chunks]) == "answer:hello"
    assert conversation.messages == [Message("user", "hello")]


async def test_registered_request_can_be_cancelled(engine: Engine) -> None:
    started = asyncio.Event()

    async def pending() -> None:
        started.set()
        await asyncio.Event().wait()

    task = asyncio.create_task(pending())
    await started.wait()
    engine.register_request("req_one", task)
    assert engine.cancel_request("req_one") is True
    with pytest.raises(asyncio.CancelledError):
        await task
    engine.unregister_request("req_one")
    assert engine.cancel_request("req_one") is False
