package cl.camodev.wosbot.main;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {
		try {
			// Initialize Log4j configuration
			configureLog4j();

			logger.info("Starting WosBot application");
			logger.info("Logging configured. Check log/bot.log for detailed logs.");

			// Launch JavaFX application
			FXApp.main(args);

		} catch (Exception e) {
			logger.error("Failed to start application: " + e.getMessage(), e);
			System.exit(1);
		}
	}

	/**
	 * Configure Log4j programmatically
	 */
	private static void configureLog4j() {
		try {
			logger.info("Log4j configuration loaded successfully");
                } catch (Exception e) {
                        System.err.println("Failed to configure Log4j: " + e.getMessage());
                }
	}
}
