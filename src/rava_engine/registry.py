from __future__ import annotations

from .errors import ModelNotFound
from .provider import Provider
from .types import ModelInfo, ProviderStatus


class ProviderRegistry:
    def __init__(self, providers: list[Provider]) -> None:
        self._providers = {provider.name: provider for provider in providers}

    def provider(self, name: str) -> Provider:
        try:
            return self._providers[name]
        except KeyError as exc:
            raise ModelNotFound(f"Unknown provider: {name}") from exc

    async def list_models(self) -> list[ModelInfo]:
        models: list[ModelInfo] = []
        for provider in self._providers.values():
            try:
                models.extend(await provider.list_models())
            except Exception:
                continue
        return sorted(models, key=lambda item: item.id)

    async def resolve_model(self, model_id: str) -> tuple[Provider, ModelInfo]:
        if "/" not in model_id:
            raise ModelNotFound("Model id must use the provider/model format")
        provider_name, upstream_id = model_id.split("/", 1)
        provider = self.provider(provider_name)
        for model in await provider.list_models():
            if model.upstream_id == upstream_id:
                return provider, model
        raise ModelNotFound(f"Model is not available to this account: {model_id}")

    async def statuses(self) -> list[ProviderStatus]:
        return [await provider.status() for provider in self._providers.values()]

    async def close(self) -> None:
        for provider in self._providers.values():
            await provider.close()
