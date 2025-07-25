package cl.camodev.utiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal thread-safe event bus implementation.
 */
public class SimpleEventBus implements EventBus {
    private final Map<Class<?>, List<Consumer<?>>> listeners = new HashMap<>();

    @Override
    public synchronized <T> void register(Class<T> type, Consumer<T> listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);
    }

    @Override
    public synchronized <T> void unregister(Class<T> type, Consumer<T> listener) {
        List<Consumer<?>> list = listeners.get(type);
        if (list != null) {
            list.remove(listener);
        }
    }

    @Override
    public void post(Object event) {
        List<Consumer<?>> list;
        synchronized (this) {
            list = new ArrayList<>(listeners.getOrDefault(event.getClass(), List.of()));
        }
        for (Consumer<?> c : list) {
            @SuppressWarnings("unchecked")
            Consumer<Object> consumer = (Consumer<Object>) c;
            consumer.accept(event);
        }
    }
}
