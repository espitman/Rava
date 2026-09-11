from __future__ import annotations

import pytest
from aiohttp.test_utils import TestClient, TestServer

from rava_engine.engine import Engine
from rava_engine.errors import ProviderUnavailable
from rava_engine.http_api import create_app
from rava_engine.registry import ProviderRegistry

from .test_engine import FakeProvider


@pytest.fixture
def http_engine() -> Engine:
    return Engine(ProviderRegistry([FakeProvider()]))


async def test_http_models_are_openai_compatible(http_engine: Engine) -> None:
    async with TestClient(TestServer(create_app(http_engine))) as client:
        response = await client.get("/v1/models")
        assert response.status == 200
        payload = await response.json()
        assert payload["data"][0]["id"] == "fake/exact"


async def test_http_completion_returns_conversation_id(http_engine: Engine) -> None:
    async with TestClient(TestServer(create_app(http_engine))) as client:
        response = await client.post(
            "/v1/chat/completions",
            headers={"X-Rava-App-Id": "app.one"},
            json={
                "model": "fake/exact",
                "messages": [{"role": "user", "content": "hello"}],
            },
        )
        assert response.status == 200
        payload = await response.json()
        assert payload["conversation_id"].startswith("conv_")
        assert payload["choices"][0]["message"]["content"] == "answer:hello"


async def test_http_stream_uses_sse(http_engine: Engine) -> None:
    async with TestClient(TestServer(create_app(http_engine))) as client:
        response = await client.post(
            "/v1/chat/completions",
            headers={"X-Rava-App-Id": "app.one"},
            json={
                "model": "fake/exact",
                "messages": [{"role": "user", "content": "hello"}],
                "stream": True,
            },
        )
        assert response.status == 200
        assert response.headers["Content-Type"].startswith("text/event-stream")
        body = await response.text()
        assert '"content": "answer:"' in body
        assert body.endswith("data: [DONE]\n\n")


async def test_http_stream_encodes_provider_failure_as_valid_sse() -> None:
    class FailingProvider(FakeProvider):
        async def send(self, session, messages):
            yield "partial"
            raise ProviderUnavailable("upstream session expired")

    engine = Engine(ProviderRegistry([FailingProvider()]))
    async with TestClient(TestServer(create_app(engine))) as client:
        response = await client.post(
            "/v1/chat/completions",
            headers={"X-Rava-App-Id": "app.one"},
            json={
                "model": "fake/exact",
                "messages": [{"role": "user", "content": "hello"}],
                "stream": True,
            },
        )
        body = await response.text()
        assert response.status == 200
        assert '"finish_reason": "error"' in body
        assert "upstream session expired" in body
        assert body.endswith("data: [DONE]\n\n")


async def test_http_rejects_missing_app_identity(http_engine: Engine) -> None:
    async with TestClient(TestServer(create_app(http_engine))) as client:
        response = await client.post(
            "/v1/chat/completions",
            json={
                "model": "fake/exact",
                "messages": [{"role": "user", "content": "hello"}],
            },
        )
        assert response.status == 400
        payload = await response.json()
        assert payload["error"]["code"] == "invalid_request"
