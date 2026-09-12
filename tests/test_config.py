import json

import pytest

from rava_engine.config import Config


def test_config_reads_private_json_file(tmp_path, monkeypatch):
    path = tmp_path / "config.json"
    path.write_text(
        json.dumps(
            {
                "port": 9001,
                "chatgpt_url": None,
                "gemini_secure_1psid": "cookie-one",
                "gemini_secure_1psidts": "cookie-two",
            }
        )
    )
    monkeypatch.setenv("RAVA_CONFIG_FILE", str(path))

    config = Config.from_env()

    assert config.port == 9001
    assert config.chatgpt_url is None
    assert config.gemini_secure_1psid == "cookie-one"
    assert config.gemini_secure_1psidts == "cookie-two"
    assert config.chatgpt_project_name == "Rava"
    assert config.gemini_temporary is False


def test_environment_overrides_config_file(tmp_path, monkeypatch):
    path = tmp_path / "config.json"
    path.write_text(json.dumps({"port": 9001, "chatgpt_url": "http://from-file"}))
    monkeypatch.setenv("RAVA_CONFIG_FILE", str(path))
    monkeypatch.setenv("RAVA_PORT", "9002")
    monkeypatch.setenv("CHATGPT_WEB2API_URL", "")

    config = Config.from_env()

    assert config.port == 9002
    assert config.chatgpt_url is None


def test_invalid_config_file_is_rejected(tmp_path, monkeypatch):
    path = tmp_path / "config.json"
    path.write_text("[]")
    monkeypatch.setenv("RAVA_CONFIG_FILE", str(path))

    with pytest.raises(ValueError, match="JSON object"):
        Config.from_env()
