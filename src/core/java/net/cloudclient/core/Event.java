package net.cloudclient.core;

/**
 * Base type for everything posted on the {@link EventBus}.
 *
 * <p>Events are plain mutable objects allocated per dispatch; the client keeps
 * them tiny so that a per-frame event costs a scalar allocation at worst. If a
 * hot path ever shows up in a profiler, the fix is to reuse a single instance
 * per event type rather than to widen this class.
 */
public abstract class Event {

    private boolean cancelled;

    /**
     * Whether {@link #cancel()} is legal for this event. Cancellable events are
     * the ones the client uses to suppress vanilla behaviour; a telemetry-style
     * notification event is not cancellable by design.
     */
    public boolean isCancellable() {
        return false;
    }

    public final boolean isCancelled() {
        return cancelled;
    }

    /**
     * Cancels this event for all lower-priority listeners. Calling this on a
     * non-cancellable event is a programming error, not a user-visible failure,
     * so it throws rather than silently doing nothing.
     */
    public final void cancel() {
        if (!isCancellable()) {
            throw new IllegalStateException(
                    "Event " + getClass().getName() + " is not cancellable");
        }
        this.cancelled = true;
    }
}
