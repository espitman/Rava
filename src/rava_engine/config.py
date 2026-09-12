from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .adapters import ChatGPTWeb2APIProvider, GeminiWebProvider
from .provider import Provider


@dataclass(frozen=True, slots=True)
class Config:
    host: str = "127.0.0.1"
    port: int = 8766
    chatgpt_url: str | None = "http://127.0.0.1:8080"
    chatgpt_api_key: str | None = None
    chatgpt_project_name: str | None = "Rava"
    gemini_secure_1psid: str | None = None
    gemini_secure_1psidts: str | None = None
    gemini_temporary: bool = False

    @classmethod
    def from_env(cls) -> Config:
        file_values = _load_config_file()
        return cls(
            host=str(_setting(file_values, "RAVA_HOST", "host", "127.0.0.1")),
            port=int(_setting(file_values, "RAVA_PORT", "port", 8766)),
            chatgpt_url=_optional_string(
                _setting(
                    file_values,
                    "CHATGPT_WEB2API_URL",
                    "chatgpt_url",
                    "http://127.0.0.1:8080",
                )
            ),
            chatgpt_api_key=_optional_string(
                _setting(file_values, "CHATGPT_WEB2API_KEY", "chatgpt_api_key", None)
            ),
            chatgpt_project_name=_optional_string(
                _setting(file_values, "RAVA_CHATGPT_PROJECT", "chatgpt_project_name", "Rava")
            ),
            gemini_secure_1psid=_optional_string(
                _setting(file_values, "GEMINI_SECURE_1PSID", "gemini_secure_1psid", None)
            ),
            gemini_secure_1psidts=_optional_string(
                _setting(file_values, "GEMINI_SECURE_1PSIDTS", "gemini_secure_1psidts", None)
            ),
            gemini_temporary=_boolean(
                _setting(file_values, "RAVA_GEMINI_TEMPORARY", "gemini_temporary", False)
            ),
        )

    def providers(self) -> list[Provider]:
        providers: list[Provider] = []
        if self.chatgpt_url:
            providers.append(
                ChatGPTWeb2APIProvider(
                    self.chatgpt_url,
                    self.chatgpt_api_key,
                    project_name=self.chatgpt_project_name,
                )
            )
        if self.gemini_secure_1psid:
            providers.append(
                GeminiWebProvider(
                    self.gemini_secure_1psid,
                    self.gemini_secure_1psidts,
                    temporary=self.gemini_temporary,
                )
            )
        return providers


def _load_config_file() -> dict[str, Any]:
    path = Path(os.getenv("RAVA_CONFIG_FILE", "~/.config/rava/config.json")).expanduser()
    try:
        value = json.loads(path.read_text())
    except FileNotFoundError:
        return {}
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"Cannot read Rava config file: {path}") from exc
    if not isinstance(value, dict):
        raise ValueError(f"Rava config file must contain a JSON object: {path}")
    return value


def _setting(values: dict[str, Any], env_name: str, key: str, default: Any) -> Any:
    if env_name in os.environ:
        return os.environ[env_name]
    return values.get(key, default)


def _optional_string(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _boolean(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    normalized = str(value).strip().lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off"}:
        return False
    raise ValueError(f"Expected a boolean setting, got: {value!r}")
