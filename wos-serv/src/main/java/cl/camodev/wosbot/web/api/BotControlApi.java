package cl.camodev.wosbot.web.api;

import cl.camodev.wosbot.serv.impl.ServScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller for bot control operations.
 */
@RestController
@RequestMapping("/api/bot")
public class BotControlApi {

    private static final Logger logger = LoggerFactory.getLogger(BotControlApi.class);

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startBot() {
        try {
            logger.info("Received request to start bot");
            ServScheduler.getServices().startBot();
            return ResponseEntity.ok(Map.of("success", true, "message", "Bot started successfully"));
        } catch (Exception e) {
            logger.error("Error starting bot: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopBot() {
        try {
            logger.info("Received request to stop bot");
            ServScheduler.getServices().stopBot();
            return ResponseEntity.ok(Map.of("success", true, "message", "Bot stopped successfully"));
        } catch (Exception e) {
            logger.error("Error stopping bot: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pauseBot() {
        try {
            logger.info("Received request to pause bot");
            ServScheduler.getServices().pauseBot();
            return ResponseEntity.ok(Map.of("success", true, "message", "Bot paused successfully"));
        } catch (Exception e) {
            logger.error("Error pausing bot: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resumeBot() {
        try {
            logger.info("Received request to resume bot");
            ServScheduler.getServices().resumeBot();
            return ResponseEntity.ok(Map.of("success", true, "message", "Bot resumed successfully"));
        } catch (Exception e) {
            logger.error("Error resuming bot: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getBotStatus() {
        try {
            cl.camodev.wosbot.serv.task.TaskQueueManager queueManager = 
                ServScheduler.getServices().getQueueManager();
            boolean hasRunningQueues = queueManager.hasRunningQueues();
            
            String status = hasRunningQueues ? "running" : "stopped";
            return ResponseEntity.ok(Map.of("status", status));
        } catch (Exception e) {
            logger.error("Error getting bot status: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to get bot status"));
        }
    }
}
