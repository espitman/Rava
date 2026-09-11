class RavaError(Exception):
    """Base error exposed by the engine."""

    code = "engine_error"
    status = 500


class InvalidRequest(RavaError):
    code = "invalid_request"
    status = 400


class ModelNotFound(RavaError):
    code = "model_not_found"
    status = 404


class ConversationNotFound(RavaError):
    code = "conversation_not_found"
    status = 404


class ConversationAccessDenied(RavaError):
    code = "conversation_access_denied"
    status = 403


class ProviderUnavailable(RavaError):
    code = "provider_unavailable"
    status = 503


class ModelSelectionFailed(RavaError):
    code = "model_selection_failed"
    status = 409
