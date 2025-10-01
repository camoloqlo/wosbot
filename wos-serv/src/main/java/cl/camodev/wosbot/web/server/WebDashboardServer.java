package cl.camodev.wosbot.web.server;

import cl.camodev.wosbot.serv.impl.ServLogs;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.web.streaming.BotStateStreamingService;
import cl.camodev.wosbot.web.streaming.LogStreamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Collections;

/**
 * Singleton wrapper for the Spring Boot web dashboard server.
 * Manages the lifecycle of the Spring Boot application context and coordinates
 * all web services including SSE streaming and REST APIs.
 * 
 * Note: This class is NOT managed by Spring - it uses manual singleton pattern.
 * The actual Spring Boot application class is {@link SpringBootApp}.
 */
public class WebDashboardServer {

    private static final Logger logger = LoggerFactory.getLogger(WebDashboardServer.class);
    private static final int DEFAULT_PORT = 8080;

    private static WebDashboardServer instance;
    
    private ConfigurableApplicationContext context;
    private int port;
    private boolean running = false;

    private WebDashboardServer() {
        this.port = DEFAULT_PORT;
    }

    /**
     * Gets the singleton instance of the WebDashboardServer.
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
     * Starts the Spring Boot web server on the specified port.
     */
    public void start(int port) {
        if (running) {
            logger.warn("Web dashboard server is already running on port {}", this.port);
            return;
        }

        this.port = port;

        try {
            SpringApplication app = new SpringApplication(SpringBootApp.class);
            app.setDefaultProperties(Collections.singletonMap("server.port", String.valueOf(port)));
            
            // Disable banner and set log level
            System.setProperty("spring.main.banner-mode", "off");
            System.setProperty("logging.level.root", "WARN");
            System.setProperty("logging.level.org.springframework", "WARN");
            
            context = app.run();
            running = true;

            // Register services as listeners after Spring context is initialized
            LogStreamingService logStreamingService = context.getBean(LogStreamingService.class);
            BotStateStreamingService botStateStreamingService = context.getBean(BotStateStreamingService.class);
            
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
     * Stops the web server.
     */
    public void stop() {
        if (!running) {
            return;
        }

        try {
            if (context != null) {
                context.close();
            }
            
            running = false;
            logger.info("Web dashboard server stopped");
        } catch (Exception e) {
            logger.error("Error stopping web dashboard server: {}", e.getMessage(), e);
        }
    }

    /**
     * Checks if the server is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the port the server is listening on.
     */
    public int getPort() {
        return port;
    }
}
