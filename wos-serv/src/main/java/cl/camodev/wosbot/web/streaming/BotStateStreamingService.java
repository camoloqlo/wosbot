package cl.camodev.wosbot.web.streaming;

import cl.camodev.wosbot.ot.DTOBotState;
import cl.camodev.wosbot.serv.IBotStateListener;
import cl.camodev.wosbot.serv.impl.ServScheduler;
import cl.camodev.wosbot.web.config.JsonSerializerConfig;
import com.google.gson.Gson;
import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service responsible for streaming bot state changes to web clients via Server-Sent Events (SSE).
 * Listens to bot state changes and broadcasts them to all connected clients.
 */
public class BotStateStreamingService implements IBotStateListener {

    private static final Logger logger = LoggerFactory.getLogger(BotStateStreamingService.class);

    private final Queue<SseClient> clients = new ConcurrentLinkedQueue<>();
    private final Gson gson;

    public BotStateStreamingService() {
        this.gson = JsonSerializerConfig.getGson();
    }

    /**
     * Handles new SSE client connections.
     * Sends the current bot state to the new client and registers it for future updates.
     * 
     * @param client The SSE client connection
     */
    public void handleClientConnection(SseClient client) {
        clients.add(client);
        client.keepAlive();
        
        client.onClose(() -> {
            clients.remove(client);
            logger.debug("Bot state stream client disconnected. Total clients: {}", clients.size());
        });
        
        // Send current bot state to new client
        sendCurrentBotState(client);
        
        logger.info("New client connected to bot state stream. Total clients: {}", clients.size());
    }

    /**
     * Sends the current bot state to a newly connected client.
     * 
     * @param client The SSE client
     */
    private void sendCurrentBotState(SseClient client) {
        try {
            cl.camodev.wosbot.serv.task.TaskQueueManager queueManager = ServScheduler.getServices().getQueueManager();
            boolean hasRunningQueues = queueManager.hasRunningQueues();
            
            DTOBotState currentState = new DTOBotState();
            currentState.setRunning(hasRunningQueues);
            currentState.setPaused(false);
            currentState.setActionTime(LocalDateTime.now());
            
            sendBotStateToClient(client, currentState);
        } catch (Exception e) {
            logger.error("Error sending current bot state to client: {}", e.getMessage());
        }
    }

    /**
     * Callback method invoked when bot state changes.
     * Broadcasts the state to all connected clients.
     * 
     * @param botState The new bot state
     */
    @Override
    public void onBotStateChange(DTOBotState botState) {
        logger.debug("Received bot state change: running={}, paused={}", botState.getRunning(), botState.getPaused());
        
        // Broadcast to all connected clients
        for (SseClient client : clients) {
            sendBotStateToClient(client, botState);
        }
    }

    /**
     * Sends bot state to a specific client.
     * 
     * @param client The SSE client
     * @param botState The bot state to send
     */
    private void sendBotStateToClient(SseClient client, DTOBotState botState) {
        try {
            String json = gson.toJson(botState);
            client.sendEvent("botState", json);
        } catch (Exception e) {
            logger.error("Error sending bot state to client: {}", e.getMessage());
            clients.remove(client);
        }
    }

    /**
     * Gets the current number of connected clients.
     * 
     * @return Number of connected clients
     */
    public int getConnectedClientsCount() {
        return clients.size();
    }

    /**
     * Clears all client connections.
     * Should be called when shutting down the service.
     */
    public void shutdown() {
        clients.clear();
        logger.info("Bot state streaming service shut down");
    }
}
