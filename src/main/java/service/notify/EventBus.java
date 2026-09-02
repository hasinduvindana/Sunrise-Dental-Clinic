package service.notify;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DESIGN PATTERN: Observer (subject side) + Singleton.
 *
 * Services publish an event and carry on; the listeners registered at start-up
 * decide what to do with it. That is what keeps "write the audit row" and
 * "push the new queue number to waiting patients" out of the booking code.
 */
public final class EventBus {

    private static final EventBus INSTANCE = new EventBus();

    private final List<ClinicEventListener> listeners = new CopyOnWriteArrayList<>();

    private EventBus() { }

    public static EventBus get() {
        return INSTANCE;
    }

    public void register(ClinicEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregister(ClinicEventListener listener) {
        listeners.remove(listener);
    }

    public int listenerCount() {
        return listeners.size();
    }

    /** A misbehaving listener must not break the action that raised the event. */
    public void publish(ClinicEvent event) {
        for (ClinicEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException e) {
                System.err.println("[EventBus] listener " + listener.getClass().getSimpleName()
                        + " failed: " + e.getMessage());
            }
        }
    }
}
