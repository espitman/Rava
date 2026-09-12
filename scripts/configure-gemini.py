#!/data/data/com.termux/files/usr/bin/python
from __future__ import annotations

import getpass
import json
import os
from pathlib import Path


def main() -> None:
    config_path = Path(
        os.environ.get("RAVA_CONFIG_FILE", "~/.config/rava/config.json")
    ).expanduser()
    try:
        config = json.loads(config_path.read_text())
    except FileNotFoundError:
        config = {}
    if not isinstance(config, dict):
        raise SystemExit(f"Config must contain a JSON object: {config_path}")

    secure_1psid = getpass.getpass("Gemini __Secure-1PSID (hidden): ").strip()
    if not secure_1psid:
        raise SystemExit("__Secure-1PSID is required")
    secure_1psidts = getpass.getpass("Gemini __Secure-1PSIDTS (hidden, optional): ").strip()

    config["gemini_secure_1psid"] = secure_1psid
    if secure_1psidts:
        config["gemini_secure_1psidts"] = secure_1psidts
    else:
        config.pop("gemini_secure_1psidts", None)
    config["gemini_temporary"] = False

    config_path.parent.mkdir(parents=True, exist_ok=True)
    temporary = config_path.with_suffix(".tmp")
    temporary.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n")
    temporary.chmod(0o600)
    temporary.replace(config_path)
    config_path.chmod(0o600)
    print(f"Gemini session saved to {config_path} with mode 0600")


if __name__ == "__main__":
    main()
