package cl.camodev.utiles;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

public class UtilTimeTest {
    @Test
    void testFormatLastExecutionNever() {
        assertEquals("Never", UtilTime.formatLastExecution(null));
    }

    @Test
    void testFormatLastExecutionJustNow() {
        String result = UtilTime.formatLastExecution(LocalDateTime.now());
        assertTrue(result.contains("ago") || result.equals("Just now"));
    }
}
