package cl.camodev.wosbot.web.api;

import cl.camodev.wosbot.serv.impl.ServScheduler;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST API controller for bot control operations.
 * Provides endpoints to start, stop, pause, resume, and check status of the bot.
 */
public class BotControlApi {

    private static final Logger logger = LoggerFactory.getLogger(BotControlApi.class);

    /**
     * Starts the bot.
     * POST /api/bot/start
     * 
     * @param ctx Javalin context
     */
    public void startBot(Context ctx) {
        try {
            logger.info("Received request to start bot");
            ServScheduler.getServices().startBot();
            ctx.json(Map.of("success", true, "message", "Bot started successfully"));
        } catch (Exception e) {
            logger.error("Error starting bot: {}", e.getMessage(), e);
            ctx.status(500).json(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Stops the bot.
     * POST /api/bot/stop
     * 
     * @param ctx Javalin context
     */
    public void stopBot(Context ctx) {
        try {
            logger.info("Received request to stop bot");
            ServScheduler.getServices().stopBot();
            ctx.json(Map.of("success", true, "message", "Bot stopped successfully"));
        } catch (Exception e) {
            logger.error("Error stopping bot: {}", e.getMessage(), e);
            ctx.status(500).json(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Pauses the bot.
     * POST /api/bot/pause
     * 
     * @param ctx Javalin context
     */
    public void pauseBot(Context ctx) {
        try {
            logger.info("Received request to pause bot");
            ServScheduler.getServices().pauseBot();
            ctx.json(Map.of("success", true, "message", "Bot paused successfully"));
        } catch (Exception e) {
            logger.error("Error pausing bot: {}", e.getMessage(), e);
            ctx.status(500).json(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Resumes the bot.
     * POST /api/bot/resume
     * 
     * @param ctx Javalin context
     */
    public void resumeBot(Context ctx) {
        try {
            logger.info("Received request to resume bot");
            ServScheduler.getServices().resumeBot();
            ctx.json(Map.of("success", true, "message", "Bot resumed successfully"));
        } catch (Exception e) {
            logger.error("Error resuming bot: {}", e.getMessage(), e);
            ctx.status(500).json(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Gets the current bot status.
     * GET /api/bot/status
     * 
     * @param ctx Javalin context
     */
    public void getBotStatus(Context ctx) {
        try {
            cl.camodev.wosbot.serv.task.TaskQueueManager queueManager = ServScheduler.getServices().getQueueManager();
            boolean hasRunningQueues = queueManager.hasRunningQueues();
            
            // Simple status check - just return running or stopped
            // The frontend will track paused state based on the pause/resume button clicks
            String status = hasRunningQueues ? "running" : "stopped";
            
            ctx.json(Map.of("status", status));
        } catch (Exception e) {
            logger.error("Error getting bot status: {}", e.getMessage(), e);
            ctx.status(500).json(Map.of("error", "Failed to get bot status"));
        }
    }
}
