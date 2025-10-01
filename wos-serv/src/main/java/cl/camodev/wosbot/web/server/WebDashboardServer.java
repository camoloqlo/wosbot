package cl.camodev.wosbot.web.server;

import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.web.api.BotControlApi;
import cl.camodev.wosbot.web.api.ProfileApi;
import cl.camodev.wosbot.web.api.TaskApi;
import cl.camodev.wosbot.web.api.VersionApi;
import cl.camodev.wosbot.web.config.JsonSerializerConfig;
import cl.camodev.wosbot.web.streaming.BotStateStreamingService;
import cl.camodev.wosbot.web.streaming.LogStreamingService;
import com.google.gson.Gson;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main web server for the WosBot dashboard.
 * Coordinates all web services including SSE streaming and REST APIs.
 * Provides a real-time web interface for monitoring and controlling the bot.
 */
public class WebDashboardServer {

    private static final Logger logger = LoggerFactory.getLogger(WebDashboardServer.class);
    private static final int DEFAULT_PORT = 8080;

    private static WebDashboardServer instance;
    
    private Javalin app;
    private int port;
    private boolean running = false;

    // Services
    private final LogStreamingService logStreamingService;
    private final BotStateStreamingService botStateStreamingService;
    
    // API Controllers
    private final BotControlApi botControlApi;
    private final ProfileApi profileApi;
    private final TaskApi taskApi;
    private final VersionApi versionApi;

    private WebDashboardServer() {
        this.port = DEFAULT_PORT;
        
        // Initialize services
        this.logStreamingService = new LogStreamingService();
        this.botStateStreamingService = new BotStateStreamingService();
        
        // Initialize API controllers
        this.botControlApi = new BotControlApi();
        this.profileApi = new ProfileApi();
        this.taskApi = new TaskApi();
        this.versionApi = new VersionApi();
    }

    /**
     * Gets the singleton instance of the WebDashboardServer.
     * 
     * @return The WebDashboardServer instance
     */
    public static WebDashboardServer getInstance() {
        if (instance == null) {
            instance = new WebDashboardServer();
        }
        return instance;
    }

    /**
     * Starts the web server on the default port (8080).
     */
    public void start() {
        start(DEFAULT_PORT);
    }

    /**
     * Starts the web server on the specified port.
     * 
     * @param port The port number to listen on
     */
    public void start(int port) {
        if (running) {
            logger.warn("Web dashboard server is already running on port {}", this.port);
            return;
        }

        this.port = port;

        try {
            // Configure Javalin with JSON mapper
            Gson gson = JsonSerializerConfig.getGson();
            app = Javalin.create(config -> {
                config.showJavalinBanner = false;
                config.staticFiles.add("/static", io.javalin.http.staticfiles.Location.CLASSPATH);
                
                // Configure JSON mapper to use Gson
                config.jsonMapper(new io.javalin.json.JsonMapper() {
                    @Override
                    public String toJsonString(Object obj, java.lang.reflect.Type type) {
                        return gson.toJson(obj, type);
                    }

                    @Override
                    public <T> T fromJsonString(String json, java.lang.reflect.Type targetType) {
                        return gson.fromJson(json, targetType);
                    }
                });
            });

            // Register SSE endpoints
            registerSseEndpoints();
            
            // Register REST API endpoints
            registerApiEndpoints();

            // Start the server
            app.start(port);
            running = true;

            // Register services as listeners
            ServLogs.getServices().addLogListener(logStreamingService);
            ServScheduler.getServices().registryBotStateListener(botStateStreamingService);

            logger.info("Web dashboard server started successfully on port {}", port);
            logger.info("Access dashboard at: http://localhost:{}", port);

        } catch (Exception e) {
            logger.error("Failed to start web dashboard server: {}", e.getMessage(), e);
            running = false;
        }
    }

    /**
     * Registers Server-Sent Events (SSE) endpoints for real-time streaming.
     */
    private void registerSseEndpoints() {
        // Log streaming endpoint
        app.sse("/logs/stream", logStreamingService::handleClientConnection);
        
        // Bot state streaming endpoint
        app.sse("/api/bot/state/stream", botStateStreamingService::handleClientConnection);
        
        logger.debug("SSE endpoints registered");
    }

    /**
     * Registers REST API endpoints.
     */
    private void registerApiEndpoints() {
        // Bot control endpoints
        app.post("/api/bot/start", botControlApi::startBot);
        app.post("/api/bot/stop", botControlApi::stopBot);
        app.post("/api/bot/pause", botControlApi::pauseBot);
        app.post("/api/bot/resume", botControlApi::resumeBot);
        app.get("/api/bot/status", botControlApi::getBotStatus);
        
        // Profile endpoints
        app.get("/api/profiles", profileApi::getProfiles);
        
        // Task endpoints
        app.get("/api/tasks", taskApi::getTasks);
        
        // Version endpoint
        app.get("/api/version", versionApi::getVersion);
        
        // Log history endpoint (REST alternative to SSE)
        app.get("/logs/history", ctx -> ctx.json(java.util.Collections.emptyList()));
        
        logger.debug("REST API endpoints registered");
    }

    /**
     * Stops the web server.
     */
    public void stop() {
        if (!running) {
            return;
        }

        try {
            if (app != null) {
                app.stop();
            }
            
            // Shutdown services
            logStreamingService.shutdown();
            botStateStreamingService.shutdown();
            
            running = false;
            logger.info("Web dashboard server stopped");
        } catch (Exception e) {
            logger.error("Error stopping web dashboard server: {}", e.getMessage(), e);
        }
    }

    /**
     * Checks if the server is currently running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the port the server is listening on.
     * 
     * @return The port number
     */
    public int getPort() {
        return port;
    }
}
