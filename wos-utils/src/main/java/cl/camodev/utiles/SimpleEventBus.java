package cl.camodev.utiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Minimal thread-safe event bus implementation. */
public class SimpleEventBus implements EventBus {
    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> listeners =
            new ConcurrentHashMap<>();

    @Override
    public <T> void register(Class<T> type, Consumer<T> listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public <T> void unregister(Class<T> type, Consumer<T> listener) {
        List<Consumer<?>> list = listeners.get(type);
        if (list != null) {
            list.remove(listener);
        }
    }

    @Override
    public void post(Object event) {
        for (Consumer<?> c :
                listeners.getOrDefault(event.getClass(), new CopyOnWriteArrayList<>())) {
            @SuppressWarnings("unchecked")
            Consumer<Object> consumer = (Consumer<Object>) c;
            consumer.accept(event);
        }
    }
}
