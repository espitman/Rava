from __future__ import annotations

from rava_engine.adapters.chatgpt import _sse_payloads


async def _lines():  # type: ignore[no-untyped-def]
    yield b": heartbeat\n"
    yield b'data: {"choices":[{"delta":{"content":"RAVA"}}]}\n'
    yield b"\n"
    yield b'data: {"conversation_id":"abc","choices":[{"delta":{},"finish_reason":"stop"}]}\n'
    yield b"data: [DONE]\n"
    yield b'data: {"ignored":true}\n'


async def test_chatgpt_sse_parser_yields_json_until_done() -> None:
    payloads = [payload async for payload in _sse_payloads(_lines())]

    assert payloads == [
        {"choices": [{"delta": {"content": "RAVA"}}]},
        {
            "conversation_id": "abc",
            "choices": [{"delta": {}, "finish_reason": "stop"}],
        },
    ]
