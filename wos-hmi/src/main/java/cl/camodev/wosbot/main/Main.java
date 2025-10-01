package cl.camodev.wosbot.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cl.camodev.wosbot.logging.ProfileLogger;
import cl.camodev.wosbot.web.server.WebDashboardServer;

public class Main {
	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {
		try {
			// Silence logback's internal status messages
			System.setProperty("logback.statusListenerClass", "ch.qos.logback.core.status.NopStatusListener");
			
			// Initialize Log4j configuration
			configureLog4j();

			logger.info("Starting WosBot application");
			logger.info("Logging configured. Check target/log/bot.log for detailed logs.");
			logger.info("Profile-specific logs will be created in target/log/profile_*.log files");
			
			// Start the log web server
			startLogWebServer();
			
 		// Add shutdown hook to close log files and web server
 		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
 			logger.info("Application shutting down, closing log files...");
 			ProfileLogger.closeAllLogWriters();
 			WebDashboardServer.getInstance().stop();
 		}));

			// Launch JavaFX application
			FXApp.main(args);

		} catch (Exception e) {
			logger.error("Failed to start application: " + e.getMessage(), e);
			ProfileLogger.closeAllLogWriters();
			System.exit(1);
		}
	}

	/**
	 * Configure Log4j programmatically
	 */
	private static void configureLog4j() {
		try {
			// Configure java.util.logging to suppress JavaFX warnings directly
			java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
			rootLogger.setLevel(java.util.logging.Level.WARNING);
			
			// Set specific loggers to SEVERE level
			java.util.logging.Logger.getLogger("javafx").setLevel(java.util.logging.Level.SEVERE);
			java.util.logging.Logger.getLogger("com.sun.javafx").setLevel(java.util.logging.Level.SEVERE);
			java.util.logging.Logger.getLogger("javax.swing").setLevel(java.util.logging.Level.SEVERE);
			
			logger.info("Log4j configuration loaded successfully");
		} catch (Exception e) {
			System.err.println("Failed to configure Log4j: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Starts the web dashboard server for real-time log viewing and bot control
	 */
	private static void startLogWebServer() {
		try {
			WebDashboardServer webDashboardServer = WebDashboardServer.getInstance();
			webDashboardServer.start(); // Starts on default port 8080
			logger.info("Web dashboard server started successfully");
		} catch (Exception e) {
			logger.error("Failed to start web dashboard server: " + e.getMessage(), e);
			// Don't fail the application if web server can't start
		}
	}
}
