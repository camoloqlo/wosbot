package cl.camodev.wosbot.web.server;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot application class for the WosBot web dashboard.
 * This class serves as the entry point for Spring Boot, allowing it to create
 * proxies and manage beans without interfering with the singleton pattern
 * used by WebDashboardServer.
 */
@SpringBootApplication
@ComponentScan(basePackages = "cl.camodev.wosbot.web")
public class SpringBootApp {
    // This class is intentionally empty - it only serves as the Spring Boot application class
    // The actual server logic is in WebDashboardServer which maintains singleton pattern
}
