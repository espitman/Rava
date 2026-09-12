package ir.rava.runtimeprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GeminiAuthLifecycleTest {
    @Test
    public void acceptsCodeOnlyAfterAuthorizationUrlIsReady() {
        GeminiAuthLifecycle lifecycle = new GeminiAuthLifecycle();

        assertFalse(lifecycle.canAcceptCode());
        assertTrue(lifecycle.begin());
        assertTrue(lifecycle.isPending());
        assertFalse(lifecycle.canAcceptCode());
        assertTrue(lifecycle.authorizationRequired());
        assertTrue(lifecycle.canAcceptCode());
        assertTrue(lifecycle.codeSubmitted());
        assertFalse(lifecycle.canAcceptCode());
        assertTrue(lifecycle.isPending());

        lifecycle.authenticated();
        assertEquals(GeminiAuthLifecycle.State.AUTHENTICATED, lifecycle.state());
        assertFalse(lifecycle.isPending());
    }

    @Test
    public void rejectsDuplicateAndOutOfOrderTransitions() {
        GeminiAuthLifecycle lifecycle = new GeminiAuthLifecycle();

        assertFalse(lifecycle.authorizationRequired());
        assertTrue(lifecycle.begin());
        assertFalse(lifecycle.begin());
        assertTrue(lifecycle.authorizationRequired());
        assertFalse(lifecycle.authorizationRequired());
        assertTrue(lifecycle.codeSubmitted());
        assertFalse(lifecycle.codeSubmitted());

        lifecycle.failed();
        assertFalse(lifecycle.isPending());
        assertTrue(lifecycle.begin());
        lifecycle.cancelled();
        assertEquals(GeminiAuthLifecycle.State.CANCELLED, lifecycle.state());
    }
}
