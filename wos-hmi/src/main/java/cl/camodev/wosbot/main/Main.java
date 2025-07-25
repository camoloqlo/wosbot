package cl.camodev.wosbot.main;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cl.camodev.utiles.EventBus;
import cl.camodev.utiles.Injector;
import cl.camodev.utiles.SimpleEventBus;

public class Main {
	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) {
                try {
                        Injector.register(EventBus.class, SimpleEventBus::new);
                        logger.info("Starting WosBot application");
                        FXApp.main(args);
                } catch (Exception e) {
                        logger.error("Failed to start application: " + e.getMessage(), e);
                        System.exit(1);
                }
        }
}
