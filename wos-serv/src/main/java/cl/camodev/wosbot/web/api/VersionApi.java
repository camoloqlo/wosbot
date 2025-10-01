package cl.camodev.wosbot.web.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for application version information.
 */
@RestController
@RequestMapping("/api")
public class VersionApi {

    private static final Logger logger = LoggerFactory.getLogger(VersionApi.class);
    
    @Value("${project.version:1.5.4}")
    private String version;

    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> getVersion() {
        try {
            return ResponseEntity.ok(Map.of("version", version));
        } catch (Exception e) {
            logger.error("Error getting application version: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "Failed to get application version"));
        }
    }
}
