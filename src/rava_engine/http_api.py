from __future__ import annotations

import asyncio
import json
import time
import uuid
from pathlib import Path
from typing import Any

from aiohttp import web

from .engine import Engine
from .errors import InvalidRequest, RavaError
from .media import MEDIA_DIR
from .types import Message

ENGINE_KEY = web.AppKey("engine", Engine)


def create_app(engine: Engine) -> web.Application:
    app = web.Application()
    app[ENGINE_KEY] = engine
    app.router.add_get("/health", _health)
    app.router.add_get("/v1/models", _models)
    app.router.add_get("/v1/media/{name}", _media)
    app.router.add_post("/v1/conversations", _create_conversation)
    app.router.add_delete("/v1/conversations/{conversation_id}", _delete_conversation)
    app.router.add_post("/v1/requests/{request_id}/cancel", _cancel_request)
    app.router.add_post("/v1/chat/completions", _chat_completions)
    app.on_cleanup.append(_cleanup)
    return app


def _engine(request: web.Request) -> Engine:
    return request.app[ENGINE_KEY]


def _app_id(request: web.Request, body: dict[str, Any] | None = None) -> str:
    value = request.headers.get("X-Rava-App-Id") or (body or {}).get("app_id")
    if not isinstance(value, str) or not value.strip():
        raise InvalidRequest("X-Rava-App-Id header or app_id field is required")
    return value.strip()


async def _health(request: web.Request) -> web.Response:
    statuses = await _engine(request).statuses()
    return web.json_response(
        {
            "status": "healthy" if any(item.available for item in statuses) else "degraded",
            "providers": [
                {"id": item.provider, "available": item.available, "detail": item.detail}
                for item in statuses
            ],
        }
    )


async def _models(request: web.Request) -> web.Response:
    models = await _engine(request).list_models()
    return web.json_response(
        {
            "object": "list",
            "data": [
                {
                    "id": item.id,
                    "object": "model",
                    "owned_by": item.provider,
                    "name": item.name,
                    "metadata": item.metadata,
                }
                for item in models
            ],
        }
    )


async def _media(request: web.Request) -> web.StreamResponse:
    try:
        _app_id(request)
        name = request.match_info["name"]
        if name != Path(name).name:
            raise InvalidRequest("Invalid media name")
        path = MEDIA_DIR / name
        if not path.is_file():
            raise web.HTTPNotFound()
        return web.FileResponse(path, headers={"Cache-Control": "private, max-age=3600"})
    except RavaError as exc:
        return _error(exc)


async def _create_conversation(request: web.Request) -> web.Response:
    try:
        body = await _json(request)
        conversation = await _engine(request).create_conversation(
            _app_id(request, body), _required_string(body, "model")
        )
        return web.json_response(_conversation_json(conversation), status=201)
    except RavaError as exc:
        return _error(exc)


async def _delete_conversation(request: web.Request) -> web.Response:
    try:
        _engine(request).conversations.delete(
            request.match_info["conversation_id"], _app_id(request)
        )
        return web.Response(status=204)
    except RavaError as exc:
        return _error(exc)


async def _cancel_request(request: web.Request) -> web.Response:
    request_id = request.match_info["request_id"]
    cancelled = _engine(request).cancel_request(request_id)
    return web.json_response({"id": request_id, "cancelled": cancelled})


async def _chat_completions(request: web.Request) -> web.StreamResponse:
    request_id = f"chatcmpl-{uuid.uuid4().hex}"
    task = asyncio.current_task()
    if task is not None:
        _engine(request).register_request(request_id, task)
    try:
        body = await _json(request)
        app_id = _app_id(request, body)
        model_id = _required_string(body, "model")
        messages = _parse_messages(body.get("messages"))
        conversation, chunks = await _engine(request).complete(
            app_id=app_id,
            model_id=model_id,
            messages=messages,
            conversation_id=body.get("conversation_id"),
        )
        if body.get("stream") is True:
            return await _stream_response(
                request, request_id, conversation.id, model_id, chunks
            )

        text = "".join([chunk async for chunk in chunks])
        return web.json_response(
            {
                "id": request_id,
                "object": "chat.completion",
                "created": int(time.time()),
                "model": model_id,
                "conversation_id": conversation.id,
                "choices": [
                    {
                        "index": 0,
                        "message": {"role": "assistant", "content": text},
                        "finish_reason": "stop",
                    }
                ],
                "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
            }
        )
    except RavaError as exc:
        return _error(exc)
    finally:
        _engine(request).unregister_request(request_id)


async def _stream_response(
    request: web.Request,
    completion_id: str,
    conversation_id: str,
    model_id: str,
    chunks: Any,
) -> web.StreamResponse:
    response = web.StreamResponse(
        status=200,
        headers={"Content-Type": "text/event-stream", "Cache-Control": "no-cache"},
    )
    await response.prepare(request)
    first = True
    try:
        async for chunk in chunks:
            payload = {
                "id": completion_id,
                "object": "chat.completion.chunk",
                "created": int(time.time()),
                "model": model_id,
                "conversation_id": conversation_id,
                "choices": [
                    {
                        "index": 0,
                        "delta": {"role": "assistant", "content": chunk}
                        if first
                        else {"content": chunk},
                        "finish_reason": None,
                    }
                ],
            }
            first = False
            await response.write(f"data: {json.dumps(payload, ensure_ascii=False)}\n\n".encode())
    except Exception as exc:
        message = str(exc) or type(exc).__name__
        payload = {
            "id": completion_id,
            "object": "chat.completion.chunk",
            "created": int(time.time()),
            "model": model_id,
            "conversation_id": conversation_id,
            "error": {"message": message, "type": "provider_stream_error"},
            "choices": [
                {
                    "index": 0,
                    "delta": {"role": "assistant", "content": message}
                    if first
                    else {"content": message},
                    "finish_reason": "error",
                }
            ],
        }
        await response.write(f"data: {json.dumps(payload, ensure_ascii=False)}\n\n".encode())
    await response.write(b"data: [DONE]\n\n")
    return response


async def _json(request: web.Request) -> dict[str, Any]:
    try:
        body = await request.json()
    except (json.JSONDecodeError, ValueError) as exc:
        raise InvalidRequest("Request body must be valid JSON") from exc
    if not isinstance(body, dict):
        raise InvalidRequest("Request body must be a JSON object")
    return body


def _required_string(body: dict[str, Any], key: str) -> str:
    value = body.get(key)
    if not isinstance(value, str) or not value.strip():
        raise InvalidRequest(f"{key} is required")
    return value.strip()


def _parse_messages(value: Any) -> list[Message]:
    if not isinstance(value, list):
        raise InvalidRequest("messages must be an array")
    messages: list[Message] = []
    for item in value:
        if not isinstance(item, dict):
            raise InvalidRequest("Every message must be an object")
        role = item.get("role")
        content = item.get("content")
        if role not in {"system", "user", "assistant"} or not isinstance(content, str):
            raise InvalidRequest("Each message requires a valid role and string content")
        messages.append(Message(role, content))
    return messages


def _conversation_json(conversation: Any) -> dict[str, Any]:
    return {
        "id": conversation.id,
        "object": "conversation",
        "app_id": conversation.app_id,
        "model": conversation.model.id,
    }


def _error(exc: RavaError) -> web.Response:
    return web.json_response(
        {"error": {"message": str(exc), "type": exc.code, "code": exc.code}}, status=exc.status
    )


async def _cleanup(app: web.Application) -> None:
    await app[ENGINE_KEY].close()
