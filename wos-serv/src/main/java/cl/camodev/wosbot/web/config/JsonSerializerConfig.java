package cl.camodev.wosbot.web.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Configuration class for JSON serialization using Gson.
 * Provides a configured Gson instance with custom adapters for Java 8 date/time types.
 */
public class JsonSerializerConfig {

    private static Gson gson;

    /**
     * Gets a configured Gson instance with custom type adapters.
     * The instance is lazily initialized and reused.
     * 
     * @return Configured Gson instance
     */
    public static Gson getGson() {
        if (gson == null) {
            gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                    .create();
        }
        return gson;
    }

    /**
     * Type adapter for Gson to serialize/deserialize LocalDateTime objects.
     * Uses ISO_LOCAL_DATE_TIME format for consistent representation.
     */
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDateTime.parse(in.nextString(), formatter);
        }
    }
}
