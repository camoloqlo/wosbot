package cl.camodev.utiles;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Very small dependency injection helper.
 */
public class Injector {
    private static final Map<Class<?>, Object> SINGLETONS = new HashMap<>();

    private Injector() {}

    public static <T> void register(Class<T> type, Supplier<T> supplier) {
        SINGLETONS.put(type, supplier.get());
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type) {
        return (T) SINGLETONS.get(type);
    }
}
