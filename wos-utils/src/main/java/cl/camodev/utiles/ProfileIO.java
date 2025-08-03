package cl.camodev.utiles;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import cl.camodev.wosbot.ot.DTOProfiles;

/**
 * Utility class for importing and exporting profiles as JSON.
 */
public final class ProfileIO {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProfileIO() {
    }

    /**
     * Writes the provided profiles to the given path in JSON format.
     *
     * @param profiles the profiles to serialize
     * @param path the destination file
     * @throws IOException if an I/O error occurs
     */
    public static void writeProfiles(List<DTOProfiles> profiles, Path path) throws IOException {
        if (profiles == null || path == null) {
            throw new IllegalArgumentException("Profiles and path must not be null");
        }
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(profiles, writer);
        }
    }

    /**
     * Reads profiles from the given JSON file.
     *
     * @param path the path to the JSON file
     * @return the list of profiles
     * @throws IOException if an I/O error occurs
     */
    public static List<DTOProfiles> readProfiles(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Path must not be null");
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            List<DTOProfiles> profiles = GSON.fromJson(reader, new TypeToken<List<DTOProfiles>>() {
            }.getType());
            return profiles != null ? profiles : List.of();
        }
    }
}
