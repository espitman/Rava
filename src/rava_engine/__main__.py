from aiohttp import web

from .config import Config
from .conversations import ConversationStore
from .engine import Engine
from .http_api import create_app
from .registry import ProviderRegistry


def main() -> None:
    config = Config.from_env()
    engine = Engine(ProviderRegistry(config.providers()), ConversationStore())
    web.run_app(create_app(engine), host=config.host, port=config.port)


if __name__ == "__main__":
    main()
