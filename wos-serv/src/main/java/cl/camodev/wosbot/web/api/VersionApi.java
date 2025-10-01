package cl.camodev.wosbot.web.api;

import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST API controller for application version information.
 * Provides endpoint to retrieve the current application version.
 */
public class VersionApi {

    private static final Logger logger = LoggerFactory.getLogger(VersionApi.class);
    private static final String DEFAULT_VERSION = "1.5.4";

    /**
     * Gets the application version.
     * GET /api/version
     * 
     * @param ctx Javalin context
     */
    public void getVersion(Context ctx) {
        try {
            String version = getApplicationVersion();
            ctx.json(Map.of("version", version));
        } catch (Exception e) {
            logger.error("Error getting application version: {}", e.getMessage(), e);
            ctx.status(500).json(Map.of("error", "Failed to get application version"));
        }
    }

    /**
     * Gets the application version from the package or returns a default value.
     * 
     * @return The application version string
     */
    private String getApplicationVersion() {
        try {
            // Try to get version from package
            Package pkg = this.getClass().getPackage();
            String version = pkg.getImplementationVersion();
            
            if (version != null && !version.isEmpty()) {
                return version;
            }
            
            // Fallback: try to read from properties file
            try (java.io.InputStream input = this.getClass().getClassLoader().getResourceAsStream("version.properties")) {
                if (input != null) {
                    java.util.Properties prop = new java.util.Properties();
                    prop.load(input);
                    version = prop.getProperty("version");
                    if (version != null && !version.isEmpty()) {
                        return version;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not determine application version: {}", e.getMessage());
        }
        
        // Default fallback version
        return DEFAULT_VERSION;
    }
}
