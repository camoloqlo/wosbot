package cl.camodev.wosbot.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cl.camodev.wosbot.logging.ProfileLogger;

public class Main {
	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {
		try {
			// Silence logback's internal status messages
			System.setProperty("logback.statusListenerClass", "ch.qos.logback.core.status.NopStatusListener");

			suppressJavaFXWarnings();

			logger.info("Starting WosBot application");
			logger.info("Logging configured. Check target/log/bot.log for detailed logs.");
			logger.info("Profile-specific logs will be created in target/log/profile_*.log files");

			// Add shutdown hook to close log files
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				logger.info("Application shutting down, closing log files...");
				ProfileLogger.closeAllLogWriters();
			}));

			// Launch JavaFX application
			FXApp.main(args);

		} catch (Exception e) {
			logger.error("Failed to start application: " + e.getMessage(), e);
			ProfileLogger.closeAllLogWriters();
			System.exit(1);
		}
	}

	private static void suppressJavaFXWarnings() {
		// Configure java.util.logging to suppress JavaFX warnings
		java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
		rootLogger.setLevel(java.util.logging.Level.WARNING);

		// Set specific loggers to SEVERE level
		java.util.logging.Logger.getLogger("javafx").setLevel(java.util.logging.Level.SEVERE);
		java.util.logging.Logger.getLogger("com.sun.javafx").setLevel(java.util.logging.Level.SEVERE);
		java.util.logging.Logger.getLogger("javax.swing").setLevel(java.util.logging.Level.SEVERE);

		logger.info("JavaFX warnings suppressed");
	}
}
