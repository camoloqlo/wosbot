package cl.camodev.wosbot.web.api;

import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.impl.ServProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API controller for profile management operations.
 */
@RestController
@RequestMapping("/api")
public class ProfileApi {

    private static final Logger logger = LoggerFactory.getLogger(ProfileApi.class);

    @GetMapping("/profiles")
    public ResponseEntity<?> getProfiles() {
        try {
            List<DTOProfiles> profiles = ServProfiles.getServices().getProfiles();
            return ResponseEntity.ok(profiles);
        } catch (Exception e) {
            logger.error("Error fetching profiles: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch profiles"));
        }
    }
}
