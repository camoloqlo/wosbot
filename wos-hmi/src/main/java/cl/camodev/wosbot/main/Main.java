package cl.camodev.wosbot.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            logger.info("Launching WosBot...");
            FXApp.main(args);
        } catch (Exception e) {
            logger.error("Startup failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

}
