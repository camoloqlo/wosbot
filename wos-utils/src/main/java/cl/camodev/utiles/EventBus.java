package cl.camodev.utiles;

import java.util.function.Consumer;

/**
 * Simple event bus interface for decoupled communication.
 */
public interface EventBus {
    <T> void register(Class<T> type, Consumer<T> listener);
    <T> void unregister(Class<T> type, Consumer<T> listener);
    void post(Object event);
}
