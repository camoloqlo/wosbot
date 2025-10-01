package cl.camodev.wosbot.web.api;

import cl.camodev.wosbot.serv.impl.ServProfiles;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST API controller for profile management operations.
 * Provides endpoints to retrieve profile information.
 */
public class ProfileApi {

    private static final Logger logger = LoggerFactory.getLogger(ProfileApi.class);

    /**
     * Gets all profiles.
     * GET /api/profiles
     * 
     * @param ctx Javalin context
     */
    public void getProfiles(Context ctx) {
        try {
            java.util.List<cl.camodev.wosbot.ot.DTOProfiles> profiles = ServProfiles.getServices().getProfiles();
            ctx.json(profiles);
        } catch (Exception e) {
            logger.error("Error fetching profiles: {}", e.getMessage(), e);
            ctx.status(500).json(Map.of("error", "Failed to fetch profiles"));
        }
    }
}
