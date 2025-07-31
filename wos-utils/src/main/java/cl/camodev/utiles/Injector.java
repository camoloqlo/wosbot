package cl.camodev.utiles;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Very small dependency injection helper. */
public class Injector {
    private static final Map<Class<?>, Object> SINGLETONS = new ConcurrentHashMap<>();

    private Injector() {}

    /**
     * Registers a singleton instance for the given type.
     *
     * @param type class to associate the instance with
     * @param supplier supplier that creates the singleton on first registration
     * @param <T> type of the singleton
     */
    public static <T> void register(Class<T> type, Supplier<T> supplier) {
        SINGLETONS.computeIfAbsent(type, t -> supplier.get());
    }

    /**
     * Retrieves a previously registered singleton instance.
     *
     * @param type class of the singleton to retrieve
     * @param <T> type of the singleton
     * @return registered instance or {@code null} if none exists
     */
    public static <T> T get(Class<T> type) {
        return type.cast(SINGLETONS.get(type));
    }
}
