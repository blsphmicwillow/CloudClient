package net.cloudclient.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small, allocation-conscious synchronous event bus with explicit priorities.
 *
 * <p>Why not just use Forge's {@code MinecraftForge.EVENT_BUS}? Because the
 * cosmetic renderer and the culling modules need ordering guarantees that are
 * not expressible through Forge's implicit registration order, and because
 * every Forge listener pays reflective dispatch costs. This bus resolves each
 * event class to a flat, pre-sorted listener array once and then does a
 * straight array walk per post.
 *
 * <p>Dispatch is synchronous and single-threaded by contract. Modules that do
 * background work (chunk meshing, pack scanning) must hand results back to the
 * main thread before posting.
 */
public final class EventBus {

    /** Higher ordinal runs earlier. */
    public enum Priority {
        HIGHEST, HIGH, NORMAL, LOW, LOWEST
    }

    public interface Listener<E extends Event> {
        void onEvent(E event);
    }

    private static final class Registration<E extends Event> {
        final Class<E> type;
        final Priority priority;
        final Listener<E> listener;
        final Object owner;
        final long sequence;
        boolean active = true;

        Registration(Class<E> type, Priority priority, Listener<E> listener,
                     Object owner, long sequence) {
            this.type = type;
            this.priority = priority;
            this.listener = listener;
            this.owner = owner;
            this.sequence = sequence;
        }
    }

    private static final Comparator<Registration<?>> ORDER =
            new Comparator<Registration<?>>() {
                @Override
                public int compare(Registration<?> a, Registration<?> b) {
                    int p = Integer.compare(a.priority.ordinal(), b.priority.ordinal());
                    return p != 0 ? p : Long.compare(a.sequence, b.sequence);
                }
            };

    private final Map<Class<?>, List<Registration<?>>> registrations = new HashMap<Class<?>, List<Registration<?>>>();
    /** event class -> flattened, sorted dispatch chain (supers included). */
    private final Map<Class<?>, List<Registration<?>>> chains = new HashMap<Class<?>, List<Registration<?>>>();
    private long sequence;
    private int version;
    private int chainVersion = -1;

    public <E extends Event> void subscribe(Class<E> type, Priority priority,
                                            Object owner, Listener<E> listener) {
        if (type == null) throw new NullPointerException("type");
        if (listener == null) throw new NullPointerException("listener");
        List<Registration<?>> list = registrations.get(type);
        if (list == null) {
            list = new ArrayList<Registration<?>>(4);
            registrations.put(type, list);
        }
        list.add(new Registration<E>(type, priority, listener, owner, sequence++));
        version++;
    }

    /** Convenience for the common "normal priority, module-owned" case. */
    public <E extends Event> void subscribe(Class<E> type, Object owner, Listener<E> listener) {
        subscribe(type, Priority.NORMAL, owner, listener);
    }

    /** Removes every listener registered by {@code owner}. */
    public void unsubscribe(Object owner) {
        boolean changed = false;
        for (List<Registration<?>> list : registrations.values()) {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).owner == owner) {
                    list.get(i).active = false;
                    list.remove(i);
                    changed = true;
                }
            }
        }
        if (changed) version++;
    }

    @SuppressWarnings("unchecked")
    public <E extends Event> E post(E event) {
        List<Registration<?>> chain = chainFor(event.getClass());
        // Snapshot length: a listener may (un)subscribe while handling, which
        // mutates `registrations` but never the cached chain list.
        for (int i = 0, n = chain.size(); i < n; i++) {
            Registration<?> reg = chain.get(i);
            if (!reg.active) continue;
            ((Listener<E>) reg.listener).onEvent(event);
            // Cancellation means "vanilla behaviour is suppressed"; lower
            // priority listeners have nothing left to contribute.
            if (event.isCancelled()) break;
        }
        return event;
    }

    /** Number of listeners that would fire for {@code type}. Diagnostics only. */
    public int listenerCount(Class<? extends Event> type) {
        return chainFor(type).size();
    }

    private List<Registration<?>> chainFor(Class<?> eventClass) {
        if (chainVersion != version) {
            chains.clear();
            chainVersion = version;
        }
        List<Registration<?>> cached = chains.get(eventClass);
        if (cached != null) return cached;

        List<Registration<?>> flat = new ArrayList<Registration<?>>(8);
        for (Class<?> c = eventClass; c != null && Event.class.isAssignableFrom(c); c = c.getSuperclass()) {
            List<Registration<?>> direct = registrations.get(c);
            if (direct != null) flat.addAll(direct);
        }
        Collections.sort(flat, ORDER);
        List<Registration<?>> frozen = Collections.unmodifiableList(flat);
        chains.put(eventClass, frozen);
        return frozen;
    }
}
