package cl.camodev.wosbot.web.streaming;

import cl.camodev.wosbot.console.list.ILogListener;
import cl.camodev.wosbot.ot.DTOLogMessage;
import cl.camodev.wosbot.web.config.JsonSerializerConfig;
import com.google.gson.Gson;
import io.javalin.http.sse.SseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service responsible for streaming logs to web clients via Server-Sent Events (SSE).
 * Maintains a history of recent logs and broadcasts new logs to all connected clients.
 */
public class LogStreamingService implements ILogListener {

    private static final Logger logger = LoggerFactory.getLogger(LogStreamingService.class);
    private static final int MAX_LOG_HISTORY = 1000;

    private final Queue<SseClient> clients = new ConcurrentLinkedQueue<>();
    private final Queue<DTOLogMessage> logHistory = new ConcurrentLinkedQueue<>();
    private final Gson gson;

    public LogStreamingService() {
        this.gson = JsonSerializerConfig.getGson();
    }

    /**
     * Handles new SSE client connections.
     * Sends log history to the new client and registers it for future updates.
     * 
     * @param client The SSE client connection
     */
    public void handleClientConnection(SseClient client) {
        clients.add(client);
        client.keepAlive();
        
        client.onClose(() -> {
            clients.remove(client);
            logger.debug("Log stream client disconnected. Total clients: {}", clients.size());
        });
        
        // Send existing log history to new client
        for (DTOLogMessage log : logHistory) {
            sendLogToClient(client, log);
        }
        
        logger.info("New client connected to log stream. Total clients: {}", clients.size());
    }

    /**
     * Callback method invoked when a new log message is received.
     * Adds the log to history and broadcasts it to all connected clients.
     * 
     * @param message The log message to broadcast
     */
    @Override
    public void onLogReceived(DTOLogMessage message) {
        logger.debug("Received log: {} - {} - {}", message.getSeverity(), message.getProfile(), message.getMessage());
        
        // Add to history (with size limit)
        logHistory.offer(message);
        while (logHistory.size() > MAX_LOG_HISTORY) {
            logHistory.poll();
        }

        // Broadcast to all connected clients
        for (SseClient client : clients) {
            sendLogToClient(client, message);
        }
    }

    /**
     * Sends a log message to a specific client.
     * 
     * @param client The SSE client
     * @param log The log message to send
     */
    private void sendLogToClient(SseClient client, DTOLogMessage log) {
        try {
            String json = gson.toJson(log);
            client.sendEvent("log", json);
        } catch (Exception e) {
            logger.error("Error sending log to client: {}", e.getMessage());
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
     * Clears all client connections and log history.
     * Should be called when shutting down the service.
     */
    public void shutdown() {
        clients.clear();
        logHistory.clear();
        logger.info("Log streaming service shut down");
    }
}
