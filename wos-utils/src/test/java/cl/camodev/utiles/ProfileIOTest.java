package cl.camodev.utiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import cl.camodev.wosbot.ot.DTOConfig;
import cl.camodev.wosbot.ot.DTOProfiles;

class ProfileIOTest {

    @Test
    void writeAndReadProfiles() throws Exception {
        DTOProfiles profile = new DTOProfiles(1L, "test", "1", true);
        profile.getConfigs().add(new DTOConfig(1L, "KEY", "value"));
        List<DTOProfiles> profiles = List.of(profile);

        Path temp = Files.createTempFile("profiles", ".json");
        ProfileIO.writeProfiles(profiles, temp);

        List<DTOProfiles> loaded = ProfileIO.readProfiles(temp);
        assertEquals(1, loaded.size());
        DTOProfiles loadedProfile = loaded.get(0);
        assertEquals("test", loadedProfile.getName());
        assertEquals("1", loadedProfile.getEmulatorNumber());
        assertTrue(loadedProfile.getEnabled());
        assertEquals("value", loadedProfile.getConfigs().get(0).getValor());
    }
}
