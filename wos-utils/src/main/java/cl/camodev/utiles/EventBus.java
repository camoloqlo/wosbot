package cl.camodev.utiles;

import java.util.function.Consumer;

/** Interface for a minimal event bus to decouple publishers from subscribers. */
public interface EventBus {

    /**
     * Registers a listener for the given event type.
     *
     * @param type event class to subscribe to
     * @param listener action invoked when the event is posted
     * @param <T> type of the event
     */
    <T> void register(Class<T> type, Consumer<T> listener);

    /**
     * Removes a previously registered listener.
     *
     * @param type event class to unsubscribe from
     * @param listener listener to remove
     * @param <T> type of the event
     */
    <T> void unregister(Class<T> type, Consumer<T> listener);

    /**
     * Dispatches an event to all registered listeners of its type.
     *
     * @param event event instance to post
     */
    void post(Object event);
}
