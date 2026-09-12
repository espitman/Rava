package ir.rava.runtimeprobe;

final class GeminiAuthLifecycle {
    enum State {
        IDLE,
        STARTING,
        AUTHORIZATION_REQUIRED,
        CODE_SUBMITTED,
        AUTHENTICATED,
        FAILED,
        CANCELLED
    }

    private State state = State.IDLE;

    synchronized boolean begin() {
        if (isPending()) return false;
        state = State.STARTING;
        return true;
    }

    synchronized boolean authorizationRequired() {
        if (state != State.STARTING) return false;
        state = State.AUTHORIZATION_REQUIRED;
        return true;
    }

    synchronized boolean codeSubmitted() {
        if (state != State.AUTHORIZATION_REQUIRED) return false;
        state = State.CODE_SUBMITTED;
        return true;
    }

    synchronized void authenticated() {
        state = State.AUTHENTICATED;
    }

    synchronized void failed() {
        state = State.FAILED;
    }

    synchronized void cancelled() {
        state = State.CANCELLED;
    }

    synchronized State state() {
        return state;
    }

    synchronized boolean canAcceptCode() {
        return state == State.AUTHORIZATION_REQUIRED;
    }

    synchronized boolean isPending() {
        return state == State.STARTING
                || state == State.AUTHORIZATION_REQUIRED
                || state == State.CODE_SUBMITTED;
    }
}
